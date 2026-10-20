package it.squadra.gestionale.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import it.squadra.gestionale.entities.Availability;
import it.squadra.gestionale.entities.Availability.AvailabilityStatus;
import it.squadra.gestionale.entities.CallupPlayer;
import it.squadra.gestionale.entities.Match;
import it.squadra.gestionale.entities.User;
import it.squadra.gestionale.entities.User.AccountStatus;
import it.squadra.gestionale.entities.User.Role;
import it.squadra.gestionale.repositories.AvailabilityRepository;
import it.squadra.gestionale.repositories.CallupPlayerRepository;
import it.squadra.gestionale.repositories.CallupRepository;
import it.squadra.gestionale.repositories.MatchRepository;
import it.squadra.gestionale.repositories.UserRepository;
import it.squadra.gestionale.security.JwtService;
import it.squadra.gestionale.support.IntegrationTest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Convocazioni: bozza, pubblicazione con avvisi, modifiche tracciate, ritiro, visibilità per ruolo. */
@IntegrationTest
class CallupControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository users;
    @Autowired
    private MatchRepository matches;
    @Autowired
    private CallupRepository callups;
    @Autowired
    private CallupPlayerRepository callupPlayers;
    @Autowired
    private AvailabilityRepository availabilities;
    @Autowired
    private JwtService jwtService;
    @Autowired
    private JdbcTemplate jdbc;

    private User admin;
    private User coach;
    private User paolo;
    private User luca;
    private User marco;
    private Match match;

    @BeforeEach
    void setUp() {
        admin = user("Anna", "Admin", Role.ADMIN, AccountStatus.ACTIVE);
        coach = user("Carlo", "Coach", Role.COACH, AccountStatus.ACTIVE);
        paolo = user("Paolo", "Bianchi", Role.PLAYER, AccountStatus.ACTIVE);
        luca = user("Luca", "Verdi", Role.PLAYER, AccountStatus.ACTIVE);
        marco = user("Marco", "Neri", Role.PLAYER, AccountStatus.ACTIVE);
        match = matches.saveAndFlush(new Match("Avversari", Instant.now().plus(5, ChronoUnit.DAYS),
                "Campo", "Via Roma 1", null, admin));
        answer(paolo, AvailabilityStatus.AVAILABLE);
        answer(luca, AvailabilityStatus.AVAILABLE);
        answer(marco, AvailabilityStatus.UNAVAILABLE);
    }

    @Nested
    class DraftAndVisibility {

        @Test
        void coachCreatesDraftInvisibleToPlayers() throws Exception {
            save(coach, null, List.of(paolo, luca, luca), false)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("DRAFT"))
                    .andExpect(jsonPath("$.revision").value(0))
                    .andExpect(jsonPath("$.players.length()").value(2))
                    .andExpect(jsonPath("$.players[0].availability").value("AVAILABLE"))
                    .andExpect(jsonPath("$.warnings.length()").value(0));

            perform(paolo, get(url(""))).andExpect(status().isNotFound());
            // La bozza non blocca le disponibilità.
            perform(paolo, get("/api/v1/matches/" + match.getId() + "/availability/me"))
                    .andExpect(jsonPath("$.editable").value(true));
        }

        @Test
        void playersCannotManageCallups() throws Exception {
            save(paolo, null, List.of(paolo), false).andExpect(status().isForbidden());
            perform(paolo, post(url("/publish")).contentType(MediaType.APPLICATION_JSON).content("{\"version\":0}"))
                    .andExpect(status().isForbidden());
            perform(paolo, get(url("/revisions"))).andExpect(status().isForbidden());
            mockMvc.perform(get(url(""))).andExpect(status().isUnauthorized());
        }

        @Test
        void inactiveUsersCannotBeConvocated() throws Exception {
            User pending = user("Piero", "Attesa", Role.PLAYER, AccountStatus.PENDING);
            save(coach, null, List.of(paolo, pending), false)
                    .andExpect(status().isUnprocessableContent());
        }

        @Test
        void cancelledMatchCannotHaveCallupChanges() throws Exception {
            match.cancel(null, admin, Instant.now());
            matches.saveAndFlush(match);
            save(coach, null, List.of(paolo), false).andExpect(status().isUnprocessableContent());
        }
    }

    @Nested
    class Publication {

        @Test
        void publishingRequiresAtLeastOnePlayer() throws Exception {
            long version = version(save(coach, null, List.of(), false));
            publish(coach, version, false)
                    .andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.detail").value(containsString("almeno un convocato")));
        }

        @Test
        void unavailableOrSilentPlayersRequireExplicitConfirmation() throws Exception {
            User silent = user("Sara", "Silente", Role.PLAYER, AccountStatus.ACTIVE);
            long version = version(save(coach, null, List.of(paolo, marco, silent), false)
                    .andExpect(jsonPath("$.warnings.length()").value(2)));

            publish(coach, version, false)
                    .andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.code").value("CALLUP_WARNINGS"))
                    .andExpect(jsonPath("$.detail").value(containsString("Marco Neri (non disponibile)")))
                    .andExpect(jsonPath("$.detail").value(containsString("Sara Silente (senza risposta)")));

            publish(coach, version, true)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("PUBLISHED"));
        }

        @Test
        void publicationRecordsAuthorRevisionAuditAndLocksAvailability() throws Exception {
            long version = version(save(coach, null, List.of(paolo, luca), false));

            publish(coach, version, false)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("PUBLISHED"))
                    .andExpect(jsonPath("$.revision").value(1))
                    .andExpect(jsonPath("$.publishedBy").value("Carlo Coach"))
                    .andExpect(jsonPath("$.publishedAt").exists());

            assertThat(count("select count(*) from callup_revisions where action = 'PUBLISHED'")).isEqualTo(1);
            assertThat(count("select count(*) from audit_logs where action = 'CALLUP_PUBLISHED'")).isEqualTo(1);
            assertThat(count("select count(*) from callup_players where added_in_revision = 1")).isEqualTo(2);
            perform(paolo, get("/api/v1/matches/" + match.getId() + "/availability/me"))
                    .andExpect(jsonPath("$.editable").value(false));
        }

        @Test
        void playersSeePublishedCallupWithoutOthersAvailability() throws Exception {
            publish(coach, version(save(coach, null, List.of(paolo, luca), false)), false);

            perform(paolo, get(url("")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.convocated").value(true))
                    .andExpect(jsonPath("$.players.length()").value(2))
                    .andExpect(jsonPath("$.players[0].availability").doesNotExist())
                    .andExpect(jsonPath("$.warnings.length()").value(0));
            perform(marco, get(url(""))).andExpect(jsonPath("$.convocated").value(false));

            perform(paolo, get("/api/v1/callups/me"))
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].match.id").value(match.getId()));
            perform(marco, get("/api/v1/callups/me")).andExpect(jsonPath("$.length()").value(0));
        }

        @Test
        void staleVersionIsConflict() throws Exception {
            long version = version(save(coach, null, List.of(paolo), false));
            save(coach, version, List.of(paolo, luca), false).andExpect(status().isOk());

            publish(coach, version, false)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("CONCURRENT_MODIFICATION"));
        }
    }

    @Nested
    class ChangesAndWithdrawal {

        @Test
        void changesToPublishedCallupAreTrackedWithoutDeletingHistory() throws Exception {
            long version = version(publish(coach, version(save(coach, null, List.of(paolo, luca), false)), false));

            String json = save(coach, version, List.of(paolo, marco), true)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("PUBLISHED"))
                    .andExpect(jsonPath("$.revision").value(2))
                    .andReturn().getResponse().getContentAsString();

            List<CallupPlayer> rows = callupPlayers.findAll();
            CallupPlayer removed = rows.stream().filter(p -> p.getUser().getId().equals(luca.getId())).findFirst()
                    .orElseThrow();
            assertThat(removed.getRemovedAt()).isNotNull();
            assertThat(removed.getRemovedInRevision()).isEqualTo(2);
            assertThat(removed.getAddedInRevision()).isEqualTo(1);
            CallupPlayer added = rows.stream().filter(p -> p.getUser().getId().equals(marco.getId())).findFirst()
                    .orElseThrow();
            assertThat(added.getAddedInRevision()).isEqualTo(2);
            assertThat(jdbc.queryForObject("select change_summary from callup_revisions where revision_number = 2",
                    String.class)).contains("aggiunti Marco Neri", "rimossi Luca Verdi");
            assertThat(count("select count(*) from audit_logs where action = 'CALLUP_UPDATED'")).isEqualTo(1);

            // Un salvataggio senza modifiche non crea revisioni.
            save(coach, ((Number) JsonPath.read(json, "$.version")).longValue(), List.of(paolo, marco), true)
                    .andExpect(jsonPath("$.revision").value(2));
        }

        @Test
        void addingUnavailablePlayerToPublishedCallupNeedsConfirmation() throws Exception {
            long version = version(publish(coach, version(save(coach, null, List.of(paolo), false)), false));

            save(coach, version, List.of(paolo, marco), false)
                    .andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.code").value("CALLUP_WARNINGS"));
        }

        @Test
        void withdrawalUnlocksAvailabilityKeepsHistoryAndAllowsRepublication() throws Exception {
            long version = version(publish(coach, version(save(coach, null, List.of(paolo), false)), false));

            String withdrawn = withdraw(coach, version, "Partita rinviata")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("WITHDRAWN"))
                    .andExpect(jsonPath("$.revision").value(2))
                    .andExpect(jsonPath("$.withdrawalReason").value("Partita rinviata"))
                    .andReturn().getResponse().getContentAsString();

            perform(paolo, get(url(""))).andExpect(jsonPath("$.status").value("WITHDRAWN"));
            perform(paolo, get("/api/v1/matches/" + match.getId() + "/availability/me"))
                    .andExpect(jsonPath("$.editable").value(true));
            assertThat(count("select count(*) from audit_logs where action = 'CALLUP_WITHDRAWN'")).isEqualTo(1);

            long draftVersion = version(save(coach, ((Number) JsonPath.read(withdrawn, "$.version")).longValue(),
                    List.of(paolo, luca), false).andExpect(jsonPath("$.status").value("DRAFT")));
            publish(coach, draftVersion, false)
                    .andExpect(jsonPath("$.status").value("PUBLISHED"))
                    .andExpect(jsonPath("$.revision").value(3));

            perform(coach, get(url("/revisions")))
                    .andExpect(jsonPath("$.length()").value(3))
                    .andExpect(jsonPath("$[0].action").value("PUBLISHED"))
                    .andExpect(jsonPath("$[1].action").value("WITHDRAWN"))
                    .andExpect(jsonPath("$[2].author").value("Carlo Coach"));
        }

        @Test
        void onlyPublishedCallupsCanBeWithdrawn() throws Exception {
            long version = version(save(coach, null, List.of(paolo), false));
            withdraw(coach, version, null).andExpect(status().isUnprocessableContent());
        }
    }

    @Nested
    class Notifications {

        @Test
        void publicationNotifiesEveryConvocatoWithMatchDetailsAndLink() throws Exception {
            publish(coach, version(save(coach, null, List.of(paolo, luca), false)), false);

            assertThat(emails()).containsExactlyInAnyOrder(
                    "CALLUP_PUBLISHED:" + paolo.getId(), "CALLUP_PUBLISHED:" + luca.getId());
            String html = jdbc.queryForObject("select payload->>'html' from email_notifications "
                    + "where recipient_user_id = ?", String.class, paolo.getId());
            assertThat(html).contains("Paolo", "Avversari", "/matches/" + match.getId(), "Maglia bianca",
                    "Luca Verdi, Paolo Bianchi");
            assertThat(jdbc.queryForObject("select subject from email_notifications where recipient_user_id = ?",
                    String.class, paolo.getId())).isEqualTo("Convocazione: vs Avversari");
        }

        @Test
        void draftChangesNotifyNobody() throws Exception {
            long version = version(save(coach, null, List.of(paolo), false));
            save(coach, version, List.of(paolo, luca), false);

            assertThat(emails()).isEmpty();
        }

        @Test
        void rosterChangeNotifiesOnlyAddedAndRemovedPlayers() throws Exception {
            long version = version(publish(coach, version(save(coach, null, List.of(paolo, luca), false)), false));
            clearEmails();

            save(coach, version, List.of(paolo, marco), true).andExpect(status().isOk());

            assertThat(emails()).containsExactlyInAnyOrder(
                    "CALLUP_PUBLISHED:" + marco.getId(), "CALLUP_REMOVED:" + luca.getId());
        }

        @Test
        void detailsChangeNotifiesRemainingPlayersOnly() throws Exception {
            long version = version(publish(coach, version(save(coach, null, List.of(paolo, luca), false)), false));
            clearEmails();

            perform(coach, put(url("")).contentType(MediaType.APPLICATION_JSON).content("""
                    {"meetingAt":"2026-11-07T14:30","notes":"Maglia blu","playerIds":[%d,%d],"version":%d}"""
                    .formatted(paolo.getId(), luca.getId(), version))).andExpect(status().isOk());

            assertThat(emails()).containsExactlyInAnyOrder(
                    "CALLUP_UPDATED:" + paolo.getId(), "CALLUP_UPDATED:" + luca.getId());
            assertThat(jdbc.queryForObject("select payload->>'text' from email_notifications where recipient_user_id = ?",
                    String.class, paolo.getId())).contains("Note: Maglia blu", "orario di ritrovo");
        }

        @Test
        void withdrawalNotifiesAllAndRepublicationNotifiesAgain() throws Exception {
            long version = version(publish(coach, version(save(coach, null, List.of(paolo, luca), false)), false));
            clearEmails();

            String withdrawn = withdraw(coach, version, "Campo inagibile").andReturn().getResponse().getContentAsString();
            assertThat(emails()).containsExactlyInAnyOrder(
                    "CALLUP_WITHDRAWN:" + paolo.getId(), "CALLUP_WITHDRAWN:" + luca.getId());
            assertThat(jdbc.queryForObject("select payload->>'text' from email_notifications where recipient_user_id = ?",
                    String.class, paolo.getId())).contains("Motivo: Campo inagibile");
            clearEmails();

            long draft = version(save(coach, ((Number) JsonPath.read(withdrawn, "$.version")).longValue(),
                    List.of(paolo, luca), false));
            publish(coach, draft, false);
            assertThat(emails()).containsExactlyInAnyOrder(
                    "CALLUP_PUBLISHED:" + paolo.getId(), "CALLUP_PUBLISHED:" + luca.getId());
        }

        @Test
        void matchScheduleChangeCreatesRevisionAndNotifiesConvocati() throws Exception {
            publish(coach, version(save(coach, null, List.of(paolo), false)), false);
            clearEmails();
            long matchVersion = matches.findById(match.getId()).orElseThrow().getVersion();

            perform(admin, put("/api/v1/matches/" + match.getId()).contentType(MediaType.APPLICATION_JSON).content("""
                    {"opponent":"Avversari","kickoff":"2030-05-10T18:00","venueName":"Nuovo campo",
                     "venueAddress":"Via Nuova 2","version":%d}""".formatted(matchVersion)))
                    .andExpect(status().isOk());

            assertThat(emails()).containsExactly("CALLUP_UPDATED:" + paolo.getId());
            assertThat(jdbc.queryForObject("select payload->>'text' from email_notifications", String.class))
                    .contains("Nuovo campo", "venerdì 10 maggio 2030 alle ore 18:00");
            perform(coach, get(url("/revisions"))).andExpect(jsonPath("$[0].action").value("UPDATED"));
        }

        @Test
        void matchCancellationWithdrawsPublishedCallupAndNotifies() throws Exception {
            publish(coach, version(save(coach, null, List.of(paolo, luca), false)), false);
            clearEmails();
            long matchVersion = matches.findById(match.getId()).orElseThrow().getVersion();

            perform(admin, post("/api/v1/matches/" + match.getId() + "/cancel").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"reason\":\"Maltempo\",\"version\":%d}".formatted(matchVersion)))
                    .andExpect(status().isOk());

            perform(paolo, get(url("")))
                    .andExpect(jsonPath("$.status").value("WITHDRAWN"))
                    .andExpect(jsonPath("$.withdrawalReason").value("Partita annullata: Maltempo"));
            assertThat(emails()).containsExactlyInAnyOrder(
                    "CALLUP_WITHDRAWN:" + paolo.getId(), "CALLUP_WITHDRAWN:" + luca.getId());
        }

        private List<String> emails() {
            return jdbc.queryForList("select notification_type || ':' || recipient_user_id from email_notifications "
                    + "order by id", String.class);
        }

        private void clearEmails() {
            jdbc.update("delete from email_notifications");
        }
    }

    private String url(String suffix) {
        return "/api/v1/matches/" + match.getId() + "/callup" + suffix;
    }

    private ResultActions save(User actor, Long version, List<User> players, boolean confirm) throws Exception {
        String ids = players.stream().map(u -> String.valueOf(u.getId())).collect(Collectors.joining(","));
        return perform(actor, put(url("")).contentType(MediaType.APPLICATION_JSON).content("""
                {"meetingAt":"2026-11-07T14:45","notes":"Maglia bianca","playerIds":[%s],"version":%s,"confirmWarnings":%s}"""
                .formatted(ids, version, confirm)));
    }

    private ResultActions publish(User actor, long version, boolean confirm) throws Exception {
        return perform(actor, post(url("/publish")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"version\":%d,\"confirmWarnings\":%s}".formatted(version, confirm)));
    }

    private ResultActions withdraw(User actor, long version, String reason) throws Exception {
        return perform(actor, post(url("/withdraw")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"version\":%d,\"reason\":%s}".formatted(version, reason == null ? "null" : "\"" + reason + "\"")));
    }

    private static long version(ResultActions result) throws Exception {
        return ((Number) JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.version")).longValue();
    }

    private void answer(User user, AvailabilityStatus status) {
        Availability availability = new Availability(match, user);
        availability.update(status, null, user, false);
        availabilities.saveAndFlush(availability);
    }

    private User user(String firstName, String lastName, Role role, AccountStatus status) {
        User user = new User(firstName, lastName, (firstName + "." + lastName + "@example.com").toLowerCase(), "hash");
        user.setRole(role);
        user.changeStatus(status, null, Instant.now());
        return users.saveAndFlush(user);
    }

    private Integer count(String sql) {
        return jdbc.queryForObject(sql, Integer.class);
    }

    private ResultActions perform(User actor, MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request.header("Authorization", "Bearer " + jwtService.issueAccessToken(actor).value()));
    }

}

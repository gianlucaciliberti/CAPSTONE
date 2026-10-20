package it.squadra.gestionale.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import it.squadra.gestionale.entities.Match;
import it.squadra.gestionale.entities.Match.MatchStatus;
import it.squadra.gestionale.entities.User;
import it.squadra.gestionale.entities.User.AccountStatus;
import it.squadra.gestionale.entities.User.Role;
import it.squadra.gestionale.repositories.MatchRepository;
import it.squadra.gestionale.repositories.UserRepository;
import it.squadra.gestionale.security.JwtService;
import it.squadra.gestionale.support.IntegrationTest;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Calendario delle partite: permessi, fuso orario Europe/Rome, modifiche concorrenti, annullamento e audit. */
@IntegrationTest
class MatchControllerIntegrationTest {

    private static final ZoneId ROME = ZoneId.of("Europe/Rome");

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository users;
    @Autowired
    private MatchRepository matches;
    @Autowired
    private JwtService jwtService;
    @Autowired
    private JdbcTemplate jdbc;

    private User admin;
    private User player;

    @BeforeEach
    void createUsers() {
        admin = user("admin.partite@example.com", Role.ADMIN);
        player = user("giocatore.partite@example.com", Role.PLAYER);
    }

    @Nested
    class Permissions {

        @Test
        void anonymousUsersCannotSeeTheCalendar() throws Exception {
            mockMvc.perform(get("/api/v1/matches?month=2026-11")).andExpect(status().isUnauthorized());
        }

        @Test
        void playersCanReadButNotCreateUpdateOrCancel() throws Exception {
            Match match = match("Avversari", Instant.parse("2026-11-07T14:30:00Z"));

            perform(player, get("/api/v1/matches?month=2026-11")).andExpect(status().isOk());
            perform(player, get("/api/v1/matches/" + match.getId())).andExpect(status().isOk());
            perform(player, post("/api/v1/matches").contentType(MediaType.APPLICATION_JSON).content(body("Nuova", "2026-12-01T20:00", null)))
                    .andExpect(status().isForbidden());
            perform(player, put("/api/v1/matches/" + match.getId()).contentType(MediaType.APPLICATION_JSON)
                    .content(body("Modificata", "2026-11-07T15:30", 0L)))
                    .andExpect(status().isForbidden());
            perform(player, post("/api/v1/matches/" + match.getId() + "/cancel").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"version\":0}"))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    class CreateAndRead {

        @Test
        void createInterpretsKickoffAsRomeLocalTimeAndAudits() throws Exception {
            String json = perform(admin, post("/api/v1/matches").contentType(MediaType.APPLICATION_JSON)
                            .content(body(" Real Borgata ", "2026-11-07T15:30", null)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.opponent").value("Real Borgata"))
                    .andExpect(jsonPath("$.status").value("SCHEDULED"))
                    // Novembre: ora solare, UTC+1
                    .andExpect(jsonPath("$.kickoffAt").value("2026-11-07T15:30:00+01:00"))
                    .andReturn().getResponse().getContentAsString();

            Long id = ((Number) JsonPath.read(json, "$.id")).longValue();
            assertThat(matches.findById(id).orElseThrow().getKickoffAt()).isEqualTo(Instant.parse("2026-11-07T14:30:00Z"));
            assertThat(auditCount("MATCH_CREATED", id)).isEqualTo(1);
        }

        @Test
        void summerTimeUsesPlusTwoOffset() throws Exception {
            perform(admin, post("/api/v1/matches").contentType(MediaType.APPLICATION_JSON)
                            .content(body("Estivi", "2026-06-15T21:00", null)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.kickoffAt").value("2026-06-15T21:00:00+02:00"));
        }

        @Test
        void invalidDataReturnsFieldErrors() throws Exception {
            perform(admin, post("/api/v1/matches").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"opponent\":\"\",\"venueName\":\"\",\"venueAddress\":\"\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors.opponent").exists())
                    .andExpect(jsonPath("$.fieldErrors.kickoff").exists())
                    .andExpect(jsonPath("$.fieldErrors.venueName").exists())
                    .andExpect(jsonPath("$.fieldErrors.venueAddress").exists());
        }

        @Test
        void monthUsesRomeBoundariesAndIncludesCancelledMatches() throws Exception {
            // 31/10 alle 23:30 a Roma è ancora ottobre, anche se in UTC sono le 22:30 dello stesso giorno.
            match("Ottobre tardi", LocalDate.of(2026, 10, 31).atTime(23, 30).atZone(ROME).toInstant());
            // 1/11 alle 00:30 a Roma è novembre, ma in UTC è ancora il 31/10.
            match("Novembre presto", LocalDate.of(2026, 11, 1).atTime(0, 30).atZone(ROME).toInstant());
            Match cancelled = match("Annullata", LocalDate.of(2026, 11, 20).atTime(20, 0).atZone(ROME).toInstant());
            cancelled.cancel("Campo impraticabile", admin, Instant.now());
            matches.saveAndFlush(cancelled);

            perform(player, get("/api/v1/matches?month=2026-11"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(2))
                    .andExpect(jsonPath("$[0].opponent").value("Novembre presto"))
                    .andExpect(jsonPath("$[1].status").value("CANCELLED"))
                    .andExpect(jsonPath("$[1].cancellationReason").value("Campo impraticabile"));
            perform(player, get("/api/v1/matches?month=2026-10"))
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].opponent").value("Ottobre tardi"));
        }

        @Test
        void invalidMonthIsBadRequest() throws Exception {
            perform(player, get("/api/v1/matches?month=novembre")).andExpect(status().isBadRequest());
            perform(player, get("/api/v1/matches")).andExpect(status().isBadRequest());
        }

        @Test
        void upcomingListsOnlyFutureScheduledMatchesInOrder() throws Exception {
            Instant now = Instant.now();
            match("Passata", now.minusSeconds(3 * 86400));
            match("Tra due giorni", now.plusSeconds(2 * 86400));
            match("Domani", now.plusSeconds(86400));
            Match cancelled = match("Annullata futura", now.plusSeconds(3 * 86400));
            cancelled.cancel(null, admin, now);
            matches.saveAndFlush(cancelled);

            perform(player, get("/api/v1/matches/upcoming"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(2))
                    .andExpect(jsonPath("$.content[0].opponent").value("Domani"))
                    .andExpect(jsonPath("$.content[1].opponent").value("Tra due giorni"));
        }

        @Test
        void unknownMatchIsNotFound() throws Exception {
            perform(player, get("/api/v1/matches/999999")).andExpect(status().isNotFound());
        }
    }

    @Nested
    class UpdateAndCancel {

        @Test
        void updateChangesDetailsReturnsNewVersionAndAuditsBeforeAfter() throws Exception {
            Match match = match("Vecchio nome", Instant.parse("2026-11-07T14:30:00Z"));
            long versionBefore = match.getVersion();

            perform(admin, put("/api/v1/matches/" + match.getId()).contentType(MediaType.APPLICATION_JSON)
                            .content(body("Nuovo nome", "2026-11-08T10:00", versionBefore)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.opponent").value("Nuovo nome"))
                    .andExpect(jsonPath("$.kickoffAt").value("2026-11-08T10:00:00+01:00"))
                    .andExpect(jsonPath("$.version").value(versionBefore + 1));

            String details = jdbc.queryForObject(
                    "select details::text from audit_logs where action = 'MATCH_UPDATED' and entity_id = ?",
                    String.class, String.valueOf(match.getId()));
            assertThat(details).contains("Vecchio nome", "Nuovo nome");
        }

        @Test
        void staleVersionIsConflict() throws Exception {
            Match match = match("Concorrente", Instant.parse("2026-11-07T14:30:00Z"));
            long version = match.getVersion();
            perform(admin, put("/api/v1/matches/" + match.getId()).contentType(MediaType.APPLICATION_JSON)
                    .content(body("Prima modifica", "2026-11-07T15:30", version))).andExpect(status().isOk());

            perform(admin, put("/api/v1/matches/" + match.getId()).contentType(MediaType.APPLICATION_JSON)
                            .content(body("Seconda modifica con dati vecchi", "2026-11-07T15:30", version)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("CONCURRENT_MODIFICATION"));
        }

        @Test
        void cancelKeepsMatchInHistoryWithReasonAndAudit() throws Exception {
            Match match = match("Da annullare", Instant.parse("2026-11-07T14:30:00Z"));

            perform(admin, post("/api/v1/matches/" + match.getId() + "/cancel").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"reason\":\"Maltempo\",\"version\":%d}".formatted(match.getVersion())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("CANCELLED"))
                    .andExpect(jsonPath("$.cancellationReason").value("Maltempo"));

            Match stored = matches.findById(match.getId()).orElseThrow();
            assertThat(stored.getStatus()).isEqualTo(MatchStatus.CANCELLED);
            assertThat(stored.getCancelledBy().getId()).isEqualTo(admin.getId());
            assertThat(auditCount("MATCH_CANCELLED", match.getId())).isEqualTo(1);
        }

        @Test
        void cancelledMatchCannotBeEditedOrCancelledAgain() throws Exception {
            Match match = match("Già annullata", Instant.parse("2026-11-07T14:30:00Z"));
            match.cancel(null, admin, Instant.now());
            match = matches.saveAndFlush(match);

            perform(admin, put("/api/v1/matches/" + match.getId()).contentType(MediaType.APPLICATION_JSON)
                            .content(body("Modifica", "2026-11-07T15:30", match.getVersion())))
                    .andExpect(status().isUnprocessableContent());
            perform(admin, post("/api/v1/matches/" + match.getId() + "/cancel").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"version\":%d}".formatted(match.getVersion())))
                    .andExpect(status().isUnprocessableContent());
        }
    }

    private User user(String email, Role role) {
        User user = new User("Test", "Partite", email, "hash");
        user.setRole(role);
        user.changeStatus(AccountStatus.ACTIVE, null, Instant.now());
        return users.saveAndFlush(user);
    }

    private Match match(String opponent, Instant kickoff) {
        return matches.saveAndFlush(new Match(opponent, kickoff, "Campo comunale", "Via Roma 1", null, admin));
    }

    private ResultActions perform(User actor, MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request.header("Authorization", "Bearer " + jwtService.issueAccessToken(actor).value()));
    }

    private static String body(String opponent, String kickoff, Long version) {
        return """
                {"opponent":"%s","kickoff":"%s","venueName":"Campo comunale","venueAddress":"Via Roma 1",
                 "notes":"","version":%s}""".formatted(opponent, kickoff, version);
    }

    private Integer auditCount(String action, Long matchId) {
        return jdbc.queryForObject("select count(*) from audit_logs where action = ? and entity_id = ?",
                Integer.class, action, String.valueOf(matchId));
    }
}

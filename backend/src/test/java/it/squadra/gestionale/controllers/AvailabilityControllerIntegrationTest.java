package it.squadra.gestionale.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import it.squadra.gestionale.entities.Availability;
import it.squadra.gestionale.entities.Availability.AvailabilityStatus;
import it.squadra.gestionale.entities.Callup;
import it.squadra.gestionale.entities.Match;
import it.squadra.gestionale.entities.User;
import it.squadra.gestionale.entities.User.AccountStatus;
import it.squadra.gestionale.entities.User.Role;
import it.squadra.gestionale.repositories.AvailabilityRepository;
import it.squadra.gestionale.repositories.CallupRepository;
import it.squadra.gestionale.repositories.MatchRepository;
import it.squadra.gestionale.repositories.UserRepository;
import it.squadra.gestionale.security.JwtService;
import it.squadra.gestionale.support.IntegrationTest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Disponibilità: propria, blocco dopo la pubblicazione, correzione dell'Admin, riepilogo squadra. */
@IntegrationTest
class AvailabilityControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository users;
    @Autowired
    private MatchRepository matches;
    @Autowired
    private CallupRepository callups;
    @Autowired
    private AvailabilityRepository availabilities;
    @Autowired
    private JwtService jwtService;
    @Autowired
    private JdbcTemplate jdbc;

    private User admin;
    private User coach;
    private User player;
    private Match match;

    @BeforeEach
    void setUp() {
        admin = user("Anna", "Admin", Role.ADMIN, AccountStatus.ACTIVE);
        coach = user("Carlo", "Coach", Role.COACH, AccountStatus.ACTIVE);
        player = user("Paolo", "Bianchi", Role.PLAYER, AccountStatus.ACTIVE);
        match = matches.saveAndFlush(new Match("Avversari", Instant.now().plus(5, ChronoUnit.DAYS),
                "Campo", "Via Roma 1", null, admin));
    }

    @Nested
    class Mine {

        @Test
        void withoutAnswerIsPendingAndEditable() throws Exception {
            perform(player, get(mineUrl()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("PENDING"))
                    .andExpect(jsonPath("$.editable").value(true))
                    .andExpect(jsonPath("$.lockReason").doesNotExist());
        }

        @Test
        void userCanSetAndChangeOwnAvailabilityWithNote() throws Exception {
            perform(player, put(mineUrl()).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"status\":\"AVAILABLE\",\"note\":\" arrivo alle 20 \"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("AVAILABLE"))
                    .andExpect(jsonPath("$.note").value("arrivo alle 20"))
                    .andExpect(jsonPath("$.updatedAt").exists());

            perform(player, put(mineUrl()).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"status\":\"UNAVAILABLE\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("UNAVAILABLE"));

            Availability stored = availabilities.findByMatchIdAndUserId(match.getId(), player.getId()).orElseThrow();
            assertThat(stored.getStatus()).isEqualTo(AvailabilityStatus.UNAVAILABLE);
            assertThat(stored.isAdminOverride()).isFalse();
            assertThat(availabilities.findByMatchId(match.getId())).hasSize(1);
        }

        @Test
        void missingOrInvalidStatusIsBadRequest() throws Exception {
            perform(player, put(mineUrl()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors.status").exists());
            perform(player, put(mineUrl()).contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"FORSE\"}"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void anonymousCannotAccess() throws Exception {
            mockMvc.perform(get(mineUrl())).andExpect(status().isUnauthorized());
        }
    }

    @Nested
    class Locking {

        @Test
        void publishedCallupLocksAvailabilityForPlayers() throws Exception {
            Callup callup = new Callup(match, coach);
            callup.markPublished(coach, Instant.now());
            callups.saveAndFlush(callup);

            perform(player, get(mineUrl()))
                    .andExpect(jsonPath("$.editable").value(false))
                    .andExpect(jsonPath("$.lockReason").value(containsString("convocazioni")));
            perform(player, put(mineUrl()).contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"AVAILABLE\"}"))
                    .andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.code").value("BUSINESS_RULE_VIOLATION"));
        }

        @Test
        void draftOrWithdrawnCallupDoesNotLock() throws Exception {
            Callup callup = new Callup(match, coach);
            callup.markPublished(coach, Instant.now());
            callup.markWithdrawn("Rinviata", coach, Instant.now());
            callups.saveAndFlush(callup);

            perform(player, put(mineUrl()).contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"AVAILABLE\"}"))
                    .andExpect(status().isOk());
        }

        @Test
        void cancelledOrPastMatchesAreLocked() throws Exception {
            match.cancel("Maltempo", admin, Instant.now());
            matches.saveAndFlush(match);
            perform(player, put(mineUrl()).contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"AVAILABLE\"}"))
                    .andExpect(status().isUnprocessableContent());

            Match past = matches.saveAndFlush(new Match("Passata", Instant.now().minus(1, ChronoUnit.DAYS),
                    "Campo", "Via Roma 1", null, admin));
            perform(player, put("/api/v1/matches/" + past.getId() + "/availability/me")
                            .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"AVAILABLE\"}"))
                    .andExpect(status().isUnprocessableContent());
        }
    }

    @Nested
    class AdminOverride {

        @Test
        void coachCannotOverrideOthers() throws Exception {
            perform(coach, put(overrideUrl(player)).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"status\":\"AVAILABLE\"}"))
                    .andExpect(status().isForbidden());
        }

        @Test
        void adminCanCorrectEvenAfterPublicationAndItIsAudited() throws Exception {
            Callup callup = new Callup(match, coach);
            callup.markPublished(coach, Instant.now());
            callups.saveAndFlush(callup);

            perform(admin, put(overrideUrl(player)).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"status\":\"UNAVAILABLE\",\"note\":\"Infortunio comunicato al telefono\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("UNAVAILABLE"))
                    .andExpect(jsonPath("$.adminOverride").value(true));

            Availability stored = availabilities.findByMatchIdAndUserId(match.getId(), player.getId()).orElseThrow();
            assertThat(stored.getUpdatedBy().getId()).isEqualTo(admin.getId());
            String details = jdbc.queryForObject(
                    "select details::text from audit_logs where action = 'AVAILABILITY_OVERRIDDEN'", String.class);
            assertThat(details).contains("\"before\": \"PENDING\"", "\"after\": \"UNAVAILABLE\"");
        }

        @Test
        void inactiveUserCannotBeTargeted() throws Exception {
            User suspended = user("Sergio", "Sospeso", Role.PLAYER, AccountStatus.SUSPENDED);
            perform(admin, put(overrideUrl(suspended)).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"status\":\"AVAILABLE\"}"))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    class TeamSummary {

        @Test
        void playersCannotSeeTeamSummary() throws Exception {
            perform(player, get(teamUrl())).andExpect(status().isForbidden());
        }

        @Test
        void coachSeesAllActiveUsersWithPendingForMissingAnswers() throws Exception {
            user("Zeno", "Pending", Role.PLAYER, AccountStatus.PENDING);
            perform(player, put(mineUrl()).contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"AVAILABLE\"}"));
            perform(coach, put(mineUrl()).contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"UNAVAILABLE\"}"));

            perform(coach, get(teamUrl()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.editable").value(true))
                    // Solo gli utenti attivi: admin, coach, player (non l'utente in attesa di approvazione)
                    .andExpect(jsonPath("$.players.length()").value(3))
                    .andExpect(jsonPath("$.players[0].lastName").value("Admin"))
                    .andExpect(jsonPath("$.players[0].status").value("PENDING"))
                    .andExpect(jsonPath("$.counts.AVAILABLE").value(1))
                    .andExpect(jsonPath("$.counts.UNAVAILABLE").value(1))
                    .andExpect(jsonPath("$.counts.PENDING").value(1));
        }
    }

    @Nested
    class Upcoming {

        @Test
        void listsUpcomingScheduledMatchesWithMyAnswer() throws Exception {
            Match later = matches.saveAndFlush(new Match("Dopo", Instant.now().plus(10, ChronoUnit.DAYS),
                    "Campo", "Via Roma 1", null, admin));
            perform(player, put(mineUrl()).contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"AVAILABLE\"}"));

            perform(player, get("/api/v1/availability/me/upcoming"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(2))
                    .andExpect(jsonPath("$[0].match.id").value(match.getId()))
                    .andExpect(jsonPath("$[0].availability.status").value("AVAILABLE"))
                    .andExpect(jsonPath("$[1].match.id").value(later.getId()))
                    .andExpect(jsonPath("$[1].availability.status").value("PENDING"));
        }

        @Test
        void limitIsValidated() throws Exception {
            perform(player, get("/api/v1/availability/me/upcoming?limit=500")).andExpect(status().isBadRequest());
        }
    }

    private String mineUrl() {
        return "/api/v1/matches/" + match.getId() + "/availability/me";
    }

    private String teamUrl() {
        return "/api/v1/matches/" + match.getId() + "/availability";
    }

    private String overrideUrl(User target) {
        return "/api/v1/matches/" + match.getId() + "/availability/" + target.getId();
    }

    private User user(String firstName, String lastName, Role role, AccountStatus status) {
        User user = new User(firstName, lastName, (firstName + "." + lastName + "@example.com").toLowerCase(), "hash");
        user.setRole(role);
        user.changeStatus(status, null, Instant.now());
        return users.saveAndFlush(user);
    }

    private ResultActions perform(User actor, MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request.header("Authorization", "Bearer " + jwtService.issueAccessToken(actor).value()));
    }
}

package it.squadra.gestionale.controllers;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import it.squadra.gestionale.entities.Match;
import it.squadra.gestionale.entities.User;
import it.squadra.gestionale.entities.User.AccountStatus;
import it.squadra.gestionale.entities.User.Role;
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

/** Dashboard della home: dati personali, convocazione, pagamenti e cose da fare in base al ruolo. */
@IntegrationTest
class DashboardControllerIntegrationTest {

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
    private User coach;
    private User paolo;

    @BeforeEach
    void setUp() {
        admin = user("Anna", "Admin", Role.ADMIN, AccountStatus.ACTIVE, true);
        coach = user("Carlo", "Coach", Role.COACH, AccountStatus.ACTIVE, true);
        paolo = user("Paolo", "Bianchi", Role.PLAYER, AccountStatus.ACTIVE, true);
    }

    @Nested
    class Personal {

        @Test
        void emptyDashboardForPlayerHasNoStaffTasks() throws Exception {
            perform(paolo, get("/api/v1/dashboard"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.nextMatch").doesNotExist())
                    .andExpect(jsonPath("$.lastResult").doesNotExist())
                    .andExpect(jsonPath("$.payments.outstandingCents").value(0))
                    .andExpect(jsonPath("$.staff").doesNotExist());
        }

        @Test
        void nextMatchShowsOwnAvailabilityLastResultAndPayments() throws Exception {
            Match next = match("Prossima", 48);
            match("Successiva", 24 * 7);
            Match played = match("Giocata", -24 * 5);
            played.recordResult(2, 1, admin, Instant.now());
            matches.saveAndFlush(played);
            perform(paolo, put("/api/v1/matches/" + next.getId() + "/availability/me")
                    .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"AVAILABLE\"}"))
                    .andExpect(status().isOk());
            perform(admin, post("/api/v1/payments").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"description\":\"Campo\",\"amountCents\":1500,\"userIds\":[%d]}".formatted(paolo.getId())))
                    .andExpect(status().isCreated());

            perform(paolo, get("/api/v1/dashboard"))
                    .andExpect(jsonPath("$.nextMatch.match.opponent").value("Prossima"))
                    .andExpect(jsonPath("$.nextMatch.myAvailability").value("AVAILABLE"))
                    .andExpect(jsonPath("$.nextMatch.availabilityOpen").value(true))
                    .andExpect(jsonPath("$.nextMatch.callupPublished").value(false))
                    .andExpect(jsonPath("$.nextMatch.convocated").value(false))
                    .andExpect(jsonPath("$.lastResult.opponent").value("Giocata"))
                    .andExpect(jsonPath("$.lastResult.teamGoals").value(2))
                    .andExpect(jsonPath("$.payments.outstandingCents").value(1500));
        }

        @Test
        void publishedCallupShowsWhoIsConvocatedAndLocksAvailability() throws Exception {
            Match next = match("Prossima", 48);
            String json = perform(coach, put("/api/v1/matches/" + next.getId() + "/callup")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"playerIds\":[%d],\"confirmWarnings\":true}".formatted(paolo.getId())))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            long version = ((Number) JsonPath.read(json, "$.version")).longValue();
            perform(coach, post("/api/v1/matches/" + next.getId() + "/callup/publish")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"version\":%d,\"confirmWarnings\":true}".formatted(version)))
                    .andExpect(status().isOk());

            perform(paolo, get("/api/v1/dashboard"))
                    .andExpect(jsonPath("$.nextMatch.myAvailability").value("PENDING"))
                    .andExpect(jsonPath("$.nextMatch.availabilityOpen").value(false))
                    .andExpect(jsonPath("$.nextMatch.callupPublished").value(true))
                    .andExpect(jsonPath("$.nextMatch.convocated").value(true));
            perform(coach, get("/api/v1/dashboard"))
                    .andExpect(jsonPath("$.nextMatch.convocated").value(false));
        }
    }

    @Nested
    class Staff {

        @Test
        void coachSeesMatchesWithoutCallupAndFailedEmailsButNotAdminTasks() throws Exception {
            match("Entro due settimane", 24 * 3);
            match("Troppo lontana", 24 * 30);
            failedEmail();

            perform(coach, get("/api/v1/dashboard"))
                    .andExpect(jsonPath("$.staff.matchesWithoutCallup.length()").value(1))
                    .andExpect(jsonPath("$.staff.matchesWithoutCallup[0].opponent").value("Entro due settimane"))
                    .andExpect(jsonPath("$.staff.failedEmails").value(1))
                    .andExpect(jsonPath("$.staff.resultsToRecord").doesNotExist())
                    .andExpect(jsonPath("$.staff.pendingApprovals").doesNotExist())
                    .andExpect(jsonPath("$.staff.outstandingPaymentsCents").doesNotExist());
        }

        @Test
        void adminSeesResultsToRecordApprovalsAndOutstandingPayments() throws Exception {
            match("Finita senza risultato", -3);
            match("In corso", -1);
            user("Verificato", "Attesa", Role.PLAYER, AccountStatus.PENDING, true);
            user("Non", "Verificato", Role.PLAYER, AccountStatus.PENDING, false);
            perform(admin, post("/api/v1/payments").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"description\":\"Campo\",\"amountCents\":1500,\"userIds\":[%d,%d]}"
                            .formatted(paolo.getId(), coach.getId())))
                    .andExpect(status().isCreated());

            perform(admin, get("/api/v1/dashboard"))
                    .andExpect(jsonPath("$.staff.resultsToRecord.length()").value(1))
                    .andExpect(jsonPath("$.staff.resultsToRecord[0].opponent").value("Finita senza risultato"))
                    .andExpect(jsonPath("$.staff.pendingApprovals").value(1))
                    .andExpect(jsonPath("$.staff.outstandingPaymentsCents").value(3000))
                    .andExpect(jsonPath("$.staff.failedEmails").value(0));
        }
    }

    private Match match(String opponent, int hoursFromNow) {
        return matches.saveAndFlush(new Match(opponent, Instant.now().plus(hoursFromNow, ChronoUnit.HOURS),
                "Campo", "Via Roma 1", null, admin));
    }

    private void failedEmail() {
        jdbc.update("insert into email_notifications (idempotency_key, notification_type, recipient_email, subject, status) "
                + "values ('test-failed', 'ACCOUNT_APPROVED', 'x@example.com', 'Prova', 'FAILED')");
    }

    private User user(String firstName, String lastName, Role role, AccountStatus status, boolean verified) {
        User user = new User(firstName, lastName, (firstName + "." + lastName + "@example.com").toLowerCase(), "hash");
        user.setRole(role);
        user.changeStatus(status, null, Instant.now());
        if (verified) {
            user.setEmailVerifiedAt(Instant.now());
        }
        return users.saveAndFlush(user);
    }

    private ResultActions perform(User actor, MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request.header("Authorization", "Bearer " + jwtService.issueAccessToken(actor).value()));
    }
}

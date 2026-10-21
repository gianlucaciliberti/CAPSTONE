package it.squadra.gestionale.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import it.squadra.gestionale.entities.User;
import it.squadra.gestionale.entities.User.AccountStatus;
import it.squadra.gestionale.entities.User.Role;
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

/** Pagamenti manuali: permessi, quote, chiusura automatica, annullamento, concorrenza e audit. */
@IntegrationTest
class PaymentControllerIntegrationTest {

    private static final LocalDate TODAY = LocalDate.now(ZoneId.of("Europe/Rome"));

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository users;
    @Autowired
    private JwtService jwtService;
    @Autowired
    private JdbcTemplate jdbc;

    private User admin;
    private User coach;
    private User paolo;
    private User luca;

    @BeforeEach
    void setUp() {
        admin = user("Anna", "Admin", Role.ADMIN, AccountStatus.ACTIVE);
        coach = user("Carlo", "Coach", Role.COACH, AccountStatus.ACTIVE);
        paolo = user("Paolo", "Bianchi", Role.PLAYER, AccountStatus.ACTIVE);
        luca = user("Luca", "Verdi", Role.PLAYER, AccountStatus.ACTIVE);
    }

    @Nested
    class Requests {

        @Test
        void onlyAdminCreatesAndListsRequests() throws Exception {
            create(coach, paolo).andExpect(status().isForbidden());
            perform(paolo, get("/api/v1/payments")).andExpect(status().isForbidden());

            create(admin, paolo, luca)
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.request.status").value("OPEN"))
                    .andExpect(jsonPath("$.request.amountCents").value(1500))
                    .andExpect(jsonPath("$.request.pendingCount").value(2))
                    .andExpect(jsonPath("$.request.outstandingCents").value(3000))
                    .andExpect(jsonPath("$.assignments[0].userName").value("Paolo Bianchi"))
                    .andExpect(jsonPath("$.assignments[1].userName").value("Luca Verdi"));

            perform(admin, get("/api/v1/payments?status=OPEN"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(1))
                    .andExpect(jsonPath("$.content[0].description").value("Campo ottobre"))
                    .andExpect(jsonPath("$.content[0].pendingCount").value(2));
            perform(admin, get("/api/v1/payments?status=CLOSED")).andExpect(jsonPath("$.totalElements").value(0));
            assertThat(audits("PAYMENT_REQUEST_CREATED")).isEqualTo(1);
        }

        @Test
        void recipientsMustBeActiveAndAmountPositive() throws Exception {
            User pending = user("Simone", "Nuovo", Role.PLAYER, AccountStatus.PENDING);
            create(admin, pending).andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.detail").value(containsString("utenti attivi")));
            perform(admin, post("/api/v1/payments").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"description\":\"x\",\"amountCents\":0,\"userIds\":[]}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors.amountCents").exists())
                    .andExpect(jsonPath("$.fieldErrors.userIds").exists());
        }

        @Test
        void updateChangesDetailsWithVersionAndAudit() throws Exception {
            String json = body(create(admin, paolo));
            long id = id(json, "$.request.id");
            long version = id(json, "$.request.version");

            perform(admin, put("/api/v1/payments/" + id).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"description\":\"Campo novembre\",\"dueDate\":\"%s\",\"version\":%d}"
                            .formatted(TODAY.plusDays(7), version)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.request.description").value("Campo novembre"));
            perform(admin, put("/api/v1/payments/" + id).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"description\":\"Altro\",\"version\":%d}".formatted(version)))
                    .andExpect(status().isConflict());
            assertThat(audits("PAYMENT_REQUEST_UPDATED")).isEqualTo(1);
        }

        @Test
        void cancelKeepsPaidAssignmentsAndCancelsPendingOnes() throws Exception {
            String json = body(create(admin, paolo, luca));
            long id = id(json, "$.request.id");
            json = body(confirm(id, id(json, "$.assignments[0].id"), id(json, "$.assignments[0].version"), TODAY));
            long version = id(json, "$.request.version");

            perform(admin, post("/api/v1/payments/" + id + "/cancel").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"reason\":\"Partita annullata\",\"version\":%d}".formatted(version)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.request.status").value("CANCELLED"))
                    .andExpect(jsonPath("$.request.cancellationReason").value("Partita annullata"))
                    .andExpect(jsonPath("$.request.paidCount").value(1))
                    .andExpect(jsonPath("$.request.cancelledCount").value(1));

            perform(admin, post("/api/v1/payments/" + id + "/assignments").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"userIds\":[%d]}".formatted(coach.getId())))
                    .andExpect(status().isUnprocessableContent());
        }
    }

    @Nested
    class Assignments {

        @Test
        void requestClosesWhenNoQuotaIsLeftAndReopensOnCorrection() throws Exception {
            String json = body(create(admin, paolo, luca));
            long id = id(json, "$.request.id");
            json = body(confirm(id, id(json, "$.assignments[0].id"), id(json, "$.assignments[0].version"), TODAY));

            json = body(perform(admin, post("/api/v1/payments/%d/assignments/%d/waive"
                    .formatted(id, id(json, "$.assignments[1].id"))).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"version\":%d}".formatted(id(json, "$.assignments[1].version"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.request.status").value("CLOSED"))
                    .andExpect(jsonPath("$.request.collectedCents").value(1500))
                    .andExpect(jsonPath("$.request.outstandingCents").value(0)));

            perform(admin, post("/api/v1/payments/%d/assignments/%d/reopen"
                    .formatted(id, id(json, "$.assignments[0].id"))).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"version\":%d}".formatted(id(json, "$.assignments[0].version"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.request.status").value("OPEN"))
                    .andExpect(jsonPath("$.assignments[0].status").value("PENDING"))
                    .andExpect(jsonPath("$.assignments[0].paidOn").doesNotExist());

            assertThat(jdbc.queryForObject("select details::text from audit_logs where action = 'PAYMENT_REOPENED'",
                    String.class)).contains("PAID", "CASH");
            assertThat(audits("PAYMENT_CONFIRMED")).isEqualTo(1);
            assertThat(audits("PAYMENT_WAIVED")).isEqualTo(1);
        }

        @Test
        void confirmationRulesAreEnforced() throws Exception {
            String json = body(create(admin, paolo));
            long id = id(json, "$.request.id");
            long assignmentId = id(json, "$.assignments[0].id");
            long version = id(json, "$.assignments[0].version");

            confirm(id, assignmentId, version, TODAY.plusDays(2))
                    .andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.detail").value(containsString("futuro")));
            confirm(id, assignmentId, version + 1, TODAY).andExpect(status().isConflict());
            confirm(id, assignmentId, version, TODAY).andExpect(status().isOk());
            confirm(id, assignmentId, version + 1, TODAY).andExpect(status().isUnprocessableContent());
            confirm(id, 999_999L, 0, TODAY).andExpect(status().isNotFound());
        }

        @Test
        void addingRecipientReopensClosedRequestButNotTwice() throws Exception {
            String json = body(create(admin, paolo));
            long id = id(json, "$.request.id");
            confirm(id, id(json, "$.assignments[0].id"), id(json, "$.assignments[0].version"), TODAY)
                    .andExpect(jsonPath("$.request.status").value("CLOSED"));

            assign(id, luca).andExpect(status().isOk())
                    .andExpect(jsonPath("$.request.status").value("OPEN"))
                    .andExpect(jsonPath("$.assignments.length()").value(2));
            assign(id, luca).andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.detail").value("Luca Verdi è già tra i destinatari."));
        }
    }

    @Nested
    class Mine {

        @Test
        void everyUserSeesOnlyOwnQuotasWithoutAdminNote() throws Exception {
            String json = body(perform(admin, post("/api/v1/payments").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"description\":\"Quota scaduta\",\"amountCents\":2000,\"dueDate\":\"%s\",\"userIds\":[%d,%d]}"
                            .formatted(TODAY.minusDays(1), paolo.getId(), luca.getId()))));
            long id = id(json, "$.request.id");
            create(admin, luca);
            perform(admin, post("/api/v1/payments/%d/assignments/%d/confirm".formatted(id, id(json, "$.assignments[1].id")))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"paidOn\":\"%s\",\"method\":\"CASH\",\"note\":\"nota interna\",\"version\":0}"
                            .formatted(TODAY)))
                    .andExpect(status().isOk());

            perform(paolo, get("/api/v1/payments/me"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.payments.length()").value(1))
                    .andExpect(jsonPath("$.payments[0].overdue").value(true))
                    .andExpect(jsonPath("$.outstandingCents").value(2000))
                    .andExpect(jsonPath("$.overdueCount").value(1));
            perform(luca, get("/api/v1/payments/me"))
                    .andExpect(jsonPath("$.payments.length()").value(2))
                    .andExpect(jsonPath("$.payments[0].description").value("Campo ottobre"))
                    .andExpect(jsonPath("$.payments[1].status").value("PAID"))
                    .andExpect(jsonPath("$.payments[1].overdue").value(false))
                    .andExpect(jsonPath("$.payments[1].adminNote").doesNotExist())
                    .andExpect(jsonPath("$.outstandingCents").value(1500));
            perform(paolo, get("/api/v1/payments/" + id)).andExpect(status().isForbidden());
        }
    }

    private ResultActions create(User actor, User... recipients) throws Exception {
        StringBuilder ids = new StringBuilder();
        for (User u : recipients) {
            ids.append(ids.isEmpty() ? "" : ",").append(u.getId());
        }
        return perform(actor, post("/api/v1/payments").contentType(MediaType.APPLICATION_JSON)
                .content("{\"description\":\"Campo ottobre\",\"amountCents\":1500,\"userIds\":[%s]}".formatted(ids)));
    }

    private ResultActions confirm(long requestId, long assignmentId, long version, LocalDate paidOn) throws Exception {
        return perform(admin, post("/api/v1/payments/%d/assignments/%d/confirm".formatted(requestId, assignmentId))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"paidOn\":\"%s\",\"method\":\"CASH\",\"version\":%d}".formatted(paidOn, version)));
    }

    private ResultActions assign(long requestId, User user) throws Exception {
        return perform(admin, post("/api/v1/payments/" + requestId + "/assignments").contentType(MediaType.APPLICATION_JSON)
                .content("{\"userIds\":[%d]}".formatted(user.getId())));
    }

    private int audits(String action) {
        return jdbc.queryForObject("select count(*) from audit_logs where action = ?", Integer.class, action);
    }

    private static String body(ResultActions result) throws Exception {
        return result.andReturn().getResponse().getContentAsString();
    }

    private static long id(String json, String path) {
        return ((Number) JsonPath.read(json, path)).longValue();
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

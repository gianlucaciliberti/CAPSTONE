package it.squadra.gestionale.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import it.squadra.gestionale.entities.EmailNotification;
import it.squadra.gestionale.entities.EmailNotification.DeliveryStatus;
import it.squadra.gestionale.entities.EmailNotification.EmailStatus;
import it.squadra.gestionale.entities.EmailNotification.NotificationType;
import it.squadra.gestionale.entities.User;
import it.squadra.gestionale.entities.User.AccountStatus;
import it.squadra.gestionale.entities.User.Role;
import it.squadra.gestionale.repositories.EmailNotificationRepository;
import it.squadra.gestionale.repositories.UserRepository;
import it.squadra.gestionale.security.JwtService;
import it.squadra.gestionale.support.IntegrationTest;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Stato degli invii, retry/annullamento manuali con audit, webhook Mailjet protetto. */
@IntegrationTest
class EmailControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository users;
    @Autowired
    private EmailNotificationRepository notifications;
    @Autowired
    private JwtService jwtService;
    @Autowired
    private JdbcTemplate jdbc;

    private User admin;
    private User coach;
    private User player;

    @BeforeEach
    void setUp() {
        admin = user("Anna", "Admin", Role.ADMIN);
        coach = user("Carlo", "Coach", Role.COACH);
        player = user("Paolo", "Bianchi", Role.PLAYER);
    }

    @Nested
    class Consultation {

        @Test
        void playersCannotSeeEmailStatus() throws Exception {
            perform(player, get("/api/v1/emails")).andExpect(status().isForbidden());
        }

        @Test
        void coachSeesStatusWithoutTechnicalErrorsAdminSeesThem() throws Exception {
            failed("K1", "1");

            perform(coach, get("/api/v1/emails?status=FAILED"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(1))
                    .andExpect(jsonPath("$.content[0].status").value("FAILED"))
                    .andExpect(jsonPath("$.content[0].lastError").doesNotExist())
                    .andExpect(jsonPath("$.content[0].payload").doesNotExist());
            perform(admin, get("/api/v1/emails?status=FAILED"))
                    .andExpect(jsonPath("$.content[0].lastError").value("Mailjet ha risposto 500"));
        }

        @Test
        void summaryCountsByStatusForRelatedEntity() throws Exception {
            failed("K1", "10");
            pending("K2", "10");
            pending("K3", "99");

            perform(coach, get("/api/v1/emails/summary?relatedType=CALLUP&relatedId=10"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.total").value(2))
                    .andExpect(jsonPath("$.byStatus.FAILED").value(1))
                    .andExpect(jsonPath("$.byStatus.PENDING").value(1))
                    .andExpect(jsonPath("$.byStatus.SENT").value(0));
        }
    }

    @Nested
    class Management {

        @Test
        void onlyAdminCanRetry() throws Exception {
            EmailNotification failed = failed("K1", "1");
            perform(coach, post("/api/v1/emails/" + failed.getId() + "/retry")).andExpect(status().isForbidden());
        }

        @Test
        void retryRequeuesFailedEmailAndIsAudited() throws Exception {
            EmailNotification failed = failed("K1", "1");

            perform(admin, post("/api/v1/emails/" + failed.getId() + "/retry"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("PENDING"))
                    .andExpect(jsonPath("$.attempts").value(0));

            assertThat(jdbc.queryForObject("select details::text from audit_logs where action = 'EMAIL_RETRIED'",
                    String.class)).contains("\"attemptsBefore\": 5");
        }

        @Test
        void retryIsOnlyForFailedEmails() throws Exception {
            EmailNotification pending = pending("K1", "1");
            perform(admin, post("/api/v1/emails/" + pending.getId() + "/retry"))
                    .andExpect(status().isUnprocessableContent());
        }

        @Test
        void retryAllFailedRequeuesEveryFailedEmail() throws Exception {
            failed("K1", "1");
            failed("K2", "1");
            pending("K3", "1");

            perform(admin, post("/api/v1/emails/retry-failed"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.retried").value(2));
            assertThat(notifications.findByStatus(EmailStatus.FAILED)).isEmpty();
            assertThat(jdbc.queryForObject("select count(*) from audit_logs where action = 'EMAIL_RETRIED'",
                    Integer.class)).isEqualTo(2);
        }

        @Test
        void cancelStopsQueuedEmailButNotSentOnes() throws Exception {
            EmailNotification pending = pending("K1", "1");
            perform(admin, post("/api/v1/emails/" + pending.getId() + "/cancel"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("CANCELLED"));

            EmailNotification sent = pending("K2", "1");
            sent.markSent("123", Instant.now());
            notifications.saveAndFlush(sent);
            perform(admin, post("/api/v1/emails/" + sent.getId() + "/cancel"))
                    .andExpect(status().isUnprocessableContent());
        }
    }

    @Nested
    class WebhookDisabled {

        @Test
        void withoutConfiguredSecretTheWebhookDoesNotExist() throws Exception {
            mockMvc.perform(post("/api/v1/webhooks/mailjet").contentType(MediaType.APPLICATION_JSON)
                            .header("Authorization", basic("mailjet", "qualsiasi")).content("[]"))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @TestPropertySource(properties = "app.mail.mailjet.webhook-secret=segreto-webhook-di-test")
    class Webhook {

        @Test
        void rejectsMissingOrWrongCredentials() throws Exception {
            webhook(null, "[]").andExpect(status().isUnauthorized());
            webhook(basic("mailjet", "sbagliato"), "[]").andExpect(status().isUnauthorized());
        }

        @Test
        void groupedEventsUpdateDeliveryStatusByMessageIdOrCustomId() throws Exception {
            EmailNotification byId = sent("K1", "1001");
            EmailNotification byCustomId = sent("K2", null);
            long time = Instant.now().getEpochSecond();

            webhook(basic("mailjet", "segreto-webhook-di-test"), """
                    [{"event":"open","time":%d,"MessageID":1001,"email":"a@example.com"},
                     {"event":"bounce","time":%d,"MessageID":0,"CustomID":"K2","error_related_to":"recipient",
                      "error":"user unknown","hard_bounce":true},
                     {"event":"evento_sconosciuto","time":%d,"MessageID":1001},
                     {"event":"sent","time":%d,"MessageID":424242}]""".formatted(time, time, time, time))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").value("Eventi elaborati: 2"));

            assertThat(notifications.findById(byId.getId()).orElseThrow().getDeliveryStatus())
                    .isEqualTo(DeliveryStatus.OPEN);
            EmailNotification bounced = notifications.findById(byCustomId.getId()).orElseThrow();
            assertThat(bounced.getDeliveryStatus()).isEqualTo(DeliveryStatus.BOUNCE);
            assertThat(bounced.getDeliveryError()).isEqualTo("recipient user unknown");
        }

        @Test
        void singleEventObjectIsAcceptedAndOlderEventsDoNotOverwriteNewer() throws Exception {
            EmailNotification email = sent("K1", "2002");
            long now = Instant.now().getEpochSecond();

            webhook(basic("mailjet", "segreto-webhook-di-test"),
                    "{\"event\":\"click\",\"time\":%d,\"MessageID\":2002}".formatted(now)).andExpect(status().isOk());
            webhook(basic("mailjet", "segreto-webhook-di-test"),
                    "{\"event\":\"sent\",\"time\":%d,\"MessageID\":2002}".formatted(now - 60)).andExpect(status().isOk());

            assertThat(notifications.findById(email.getId()).orElseThrow().getDeliveryStatus())
                    .isEqualTo(DeliveryStatus.CLICK);
        }

        @Test
        void malformedBodyIsBadRequest() throws Exception {
            webhook(basic("mailjet", "segreto-webhook-di-test"), "{non json").andExpect(status().isBadRequest());
        }

        private ResultActions webhook(String authorization, String body) throws Exception {
            MockHttpServletRequestBuilder request = post("/api/v1/webhooks/mailjet")
                    .contentType(MediaType.APPLICATION_JSON).content(body);
            if (authorization != null) {
                request.header("Authorization", authorization);
            }
            return mockMvc.perform(request);
        }
    }

    private EmailNotification pending(String key, String relatedId) {
        return notifications.saveAndFlush(new EmailNotification(key, NotificationType.CALLUP_PUBLISHED, player,
                player.getEmail(), player.getFullName(), "Convocazione", "{\"text\":\"x\",\"html\":\"x\"}", "CALLUP",
                relatedId, 5, Instant.now()));
    }

    private EmailNotification failed(String key, String relatedId) {
        EmailNotification email = pending(key, relatedId);
        for (int i = 0; i < 5; i++) {
            email.markAttemptFailed("Mailjet ha risposto 500", Instant.now(), Duration.ofSeconds(30));
        }
        return notifications.saveAndFlush(email);
    }

    private EmailNotification sent(String key, String providerId) {
        EmailNotification email = pending(key, "1");
        email.markSent(providerId, Instant.now().minusSeconds(3600));
        return notifications.saveAndFlush(email);
    }

    private static String basic(String user, String password) {
        return "Basic " + Base64.getEncoder().encodeToString((user + ":" + password).getBytes(StandardCharsets.UTF_8));
    }

    private User user(String firstName, String lastName, Role role) {
        User user = new User(firstName, lastName, (firstName + "." + lastName + "@example.com").toLowerCase(), "hash");
        user.setRole(role);
        user.changeStatus(AccountStatus.ACTIVE, null, Instant.now());
        return users.saveAndFlush(user);
    }

    private ResultActions perform(User actor, MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request.header("Authorization", "Bearer " + jwtService.issueAccessToken(actor).value()));
    }
}

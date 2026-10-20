package it.squadra.gestionale.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import it.squadra.gestionale.config.AppProperties;
import it.squadra.gestionale.services.EmailSender.MailjetEmailSender;
import it.squadra.gestionale.services.EmailSender.OutgoingEmail;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.ZoneId;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/** Invio con Mailjet Send API v3.1, verificato con un server HTTP simulato (nessuna chiamata reale). */
class MailjetEmailSenderTest {

    private static final OutgoingEmail EMAIL = new OutgoingEmail("mario@example.com", "Mario Rossi",
            "Convocazione: vs Real Borgata", "Testo", "<p>HTML</p>", "CALLUP_PUBLISHED:c1:r1:u7");

    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

    @Test
    void sendsAuthenticatedRequestAndReturnsMessageId() {
        String expectedAuth = "Basic " + Base64.getEncoder()
                .encodeToString("chiave:segreto".getBytes(StandardCharsets.UTF_8));
        server.expect(requestTo(MailjetEmailSender.SEND_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", expectedAuth))
                .andExpect(content().json("""
                        {"Messages":[{"From":{"Email":"noreply@squadra.example","Name":"ASD Squadra"},
                          "To":[{"Email":"mario@example.com","Name":"Mario Rossi"}],
                          "Subject":"Convocazione: vs Real Borgata","TextPart":"Testo","HTMLPart":"<p>HTML</p>",
                          "CustomID":"CALLUP_PUBLISHED:c1:r1:u7"}]}"""))
                .andRespond(withSuccess("""
                        {"Messages":[{"Status":"success","To":[{"Email":"mario@example.com",
                          "MessageUUID":"abc","MessageID":288230376151711744,"MessageHref":"x"}]}]}""",
                        MediaType.APPLICATION_JSON));

        String messageId = sender("chiave", "segreto").send(EMAIL);

        assertThat(messageId).isEqualTo("288230376151711744");
        server.verify();
    }

    @Test
    void httpErrorBecomesExceptionWithStatusForRetry() {
        server.expect(requestTo(MailjetEmailSender.SEND_URL))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED).body("{\"ErrorMessage\":\"API key non valida\"}")
                        .contentType(MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> sender("chiave", "sbagliato").send(EMAIL))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("401")
                .hasMessageContaining("API key non valida");
    }

    @Test
    void rejectedMessageIsAnError() {
        server.expect(requestTo(MailjetEmailSender.SEND_URL))
                .andRespond(withSuccess("{\"Messages\":[{\"Status\":\"error\",\"Errors\":[{\"ErrorMessage\":\"x\"}]}]}",
                        MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> sender("chiave", "segreto").send(EMAIL))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("rifiutato");
    }

    @Test
    void missingCredentialsPreventStartup() {
        assertThatThrownBy(() -> sender("", null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("MAILJET_API_KEY");
    }

    private MailjetEmailSender sender(String apiKey, String apiSecret) {
        AppProperties properties = new AppProperties(ZoneId.of("Europe/Rome"), "https://squadra.example",
                new AppProperties.Cors(List.of("https://squadra.example")),
                new AppProperties.Security(new AppProperties.Jwt("x", "t", Duration.ofMinutes(15), Duration.ofDays(1)), true),
                new AppProperties.RateLimit(false, List.of()),
                new AppProperties.BootstrapAdmin(null, null, null, null),
                new AppProperties.Mail(AppProperties.MailMode.MAILJET, "noreply@squadra.example", "ASD Squadra", 5, true,
                        new AppProperties.Mailjet(apiKey, apiSecret, null)),
                new AppProperties.OpenAi(false, null, null, Duration.ofSeconds(20)));
        return new MailjetEmailSender(properties, builder);
    }
}

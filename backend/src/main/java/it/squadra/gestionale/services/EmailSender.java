package it.squadra.gestionale.services;

import it.squadra.gestionale.config.AppProperties;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Invio effettivo di una email. L'implementazione dipende da {@code app.mail.mode}:
 * LOG (nessun invio reale, sviluppo e test) oppure MAILJET (invio tramite Mailjet Send API v3.1).
 */
public interface EmailSender {

    /**
     * Invia l'email e restituisce l'id assegnato dal provider (se disponibile).
     * Un'eccezione indica un invio fallito: il worker riproverà più tardi.
     */
    String send(OutgoingEmail email) throws Exception;

    record OutgoingEmail(String toEmail, String toName, String subject, String textBody, String htmlBody,
                         String customId) {
    }

    /**
     * Modalità senza invii reali: l'email viene solo registrata nei log.
     * Il corpo (che può contenere link con token) è scritto solo a livello DEBUG, attivo nel profilo dev.
     */
    @Component
    @ConditionalOnProperty(name = "app.mail.mode", havingValue = "LOG", matchIfMissing = true)
    class LogEmailSender implements EmailSender {

        private static final Logger log = LoggerFactory.getLogger(LogEmailSender.class);

        @Override
        public String send(OutgoingEmail email) {
            log.info("[MAIL_MODE=LOG] Email non inviata davvero -> a: {} | oggetto: {}", email.toEmail(), email.subject());
            log.debug("[MAIL_MODE=LOG] Testo dell'email per {}:\n{}", email.toEmail(), email.textBody());
            return "log-" + email.customId();
        }
    }

    /**
     * Invio reale con Mailjet Send API v3.1 (autenticazione Basic con API key e secret key).
     * Restituisce il MessageID di Mailjet, usato per collegare gli eventi dei webhook all'email.
     * Le credenziali sono obbligatorie: se mancano il backend non si avvia.
     */
    @Component
    @ConditionalOnProperty(name = "app.mail.mode", havingValue = "MAILJET")
    class MailjetEmailSender implements EmailSender {

        static final String SEND_URL = "https://api.mailjet.com/v3.1/send";
        private static final JsonMapper JSON = JsonMapper.builder().build();

        private final RestClient client;
        private final String fromEmail;
        private final String fromName;

        @Autowired
        public MailjetEmailSender(AppProperties properties) {
            this(properties, RestClient.builder().requestFactory(requestFactory()));
        }

        /** Costruttore usato nei test per intercettare le chiamate HTTP. */
        MailjetEmailSender(AppProperties properties, RestClient.Builder builder) {
            AppProperties.Mailjet mailjet = properties.mail().mailjet();
            if (isBlank(mailjet.apiKey()) || isBlank(mailjet.apiSecret())) {
                throw new IllegalStateException("MAIL_MODE=MAILJET richiede MAILJET_API_KEY e MAILJET_API_SECRET.");
            }
            this.fromEmail = properties.mail().fromEmail();
            this.fromName = properties.mail().fromName();
            this.client = builder
                    .defaultHeaders(headers -> headers.setBasicAuth(mailjet.apiKey(), mailjet.apiSecret()))
                    .build();
        }

        @Override
        public String send(OutgoingEmail email) {
            Map<String, Object> message = Map.of(
                    "From", Map.of("Email", fromEmail, "Name", fromName),
                    "To", List.of(recipient(email)),
                    "Subject", email.subject(),
                    "TextPart", email.textBody(),
                    "HTMLPart", email.htmlBody(),
                    // Identificativo nostro (chiave di idempotenza), restituito anche negli eventi dei webhook.
                    "CustomID", email.customId());
            String response = client.post()
                    .uri(URI.create(SEND_URL))
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(JSON.writeValueAsString(Map.of("Messages", List.of(message))))
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (request, resp) -> {
                        throw new IllegalStateException("Mailjet ha risposto " + resp.getStatusCode().value() + ": "
                                + truncate(new String(resp.getBody().readAllBytes()), 400));
                    })
                    .body(String.class);

            JsonNode result = JSON.readTree(response).path("Messages").path(0);
            if (!"success".equals(result.path("Status").asString())) {
                throw new IllegalStateException("Invio rifiutato da Mailjet: " + truncate(result.toString(), 400));
            }
            JsonNode messageId = result.path("To").path(0).path("MessageID");
            return messageId.isMissingNode() || messageId.isNull() ? null : messageId.asString();
        }

        private static Map<String, String> recipient(OutgoingEmail email) {
            return email.toName() == null
                    ? Map.of("Email", email.toEmail())
                    : Map.of("Email", email.toEmail(), "Name", email.toName());
        }

        private static JdkClientHttpRequestFactory requestFactory() {
            HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
            JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
            factory.setReadTimeout(Duration.ofSeconds(20));
            return factory;
        }

        private static boolean isBlank(String value) {
            return value == null || value.isBlank();
        }

        private static String truncate(String value, int max) {
            return value.length() <= max ? value : value.substring(0, max) + "...";
        }
    }
}

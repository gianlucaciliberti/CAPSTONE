package it.squadra.gestionale.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.time.ZoneId;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Configurazione applicativa tipizzata (prefisso {@code app}). Validata all'avvio:
 * un valore mancante o non valido blocca l'applicazione invece di emergere a runtime.
 */
@Validated
@ConfigurationProperties(prefix = "app")
public record AppProperties(
        @NotNull ZoneId timezone,
        @NotBlank String frontendUrl,
        @Valid @NotNull Cors cors,
        @Valid @NotNull Security security,
        @Valid @NotNull RateLimit rateLimit,
        @NotNull BootstrapAdmin bootstrapAdmin,
        @Valid @NotNull Mail mail,
        @Valid @NotNull OpenAi openai) {

    public record Cors(@NotEmpty List<String> allowedOrigins) {
    }

    public record Security(@Valid @NotNull Jwt jwt, boolean cookieSecure) {
    }

    /** Il segreto viene verificato (presenza e lunghezza) quando si creano encoder e decoder JWT. */
    public record Jwt(
            String secret,
            @NotBlank String issuer,
            @NotNull Duration accessTokenTtl,
            @NotNull Duration refreshTokenTtl) {
    }

    /** Limiti di richieste per IP (prefisso {@code app.rate-limit}); ogni regola vale per le POST sul percorso. */
    public record RateLimit(boolean enabled, @NotNull List<@Valid Rule> rules) {

        public record Rule(@NotBlank String path, @Min(1) int capacity, @NotNull Duration window) {
        }
    }

    /**
     * Dati del primo Admin (prefisso {@code app.bootstrap-admin}, variabili ADMIN_BOOTSTRAP_*).
     * Usati solo se valorizzati e solo finché non esiste alcun Admin; vanno rimossi dopo il primo avvio.
     */
    public record BootstrapAdmin(String email, String password, String firstName, String lastName) {

        public boolean isConfigured() {
            return email != null && !email.isBlank() && password != null && !password.isBlank();
        }
    }

    public enum MailMode {
        /** Nessun invio reale: le email vengono solo registrate nei log. */
        LOG,
        MAILJET
    }

    /** {@code workerEnabled}: se false le email restano in coda senza essere inviate (usato nei test). */
    public record Mail(
            @NotNull MailMode mode,
            @NotBlank String fromEmail,
            @NotBlank String fromName,
            @Min(1) int maxAttempts,
            boolean workerEnabled,
            @Valid @NotNull Mailjet mailjet) {
    }

    public record Mailjet(String apiKey, String apiSecret, String webhookSecret) {
    }

    public record OpenAi(boolean enabled, String apiKey, String model, @NotNull Duration timeout) {
    }
}

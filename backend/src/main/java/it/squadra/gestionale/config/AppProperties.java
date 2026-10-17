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
        @Valid @NotNull Mail mail,
        @Valid @NotNull OpenAi openai) {

    public record Cors(@NotEmpty List<String> allowedOrigins) {
    }

    public record Security(@Valid @NotNull Jwt jwt, boolean cookieSecure) {
    }

    /** Il segreto è obbligatorio solo quando viene usato (fase autenticazione), non per avviare lo schema. */
    public record Jwt(
            String secret,
            @NotBlank String issuer,
            @NotNull Duration accessTokenTtl,
            @NotNull Duration refreshTokenTtl) {
    }

    public enum MailMode {
        /** Nessun invio reale: le email vengono solo registrate nei log. */
        LOG,
        MAILJET
    }

    public record Mail(
            @NotNull MailMode mode,
            @NotBlank String fromEmail,
            @NotBlank String fromName,
            @Min(1) int maxAttempts,
            @Valid @NotNull Mailjet mailjet) {
    }

    public record Mailjet(String apiKey, String apiSecret, String webhookSecret) {
    }

    public record OpenAi(boolean enabled, String apiKey, String model, @NotNull Duration timeout) {
    }
}

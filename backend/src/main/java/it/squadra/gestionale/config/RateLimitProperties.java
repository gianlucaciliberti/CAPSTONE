package it.squadra.gestionale.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Limiti di richieste per IP sugli endpoint sensibili (prefisso {@code app.rate-limit}).
 * Ogni regola si applica alle richieste POST sul percorso indicato.
 */
@Validated
@ConfigurationProperties(prefix = "app.rate-limit")
public record RateLimitProperties(boolean enabled, @NotNull List<@Valid Rule> rules) {

    public record Rule(@NotBlank String path, @Min(1) int capacity, @NotNull Duration window) {
    }
}

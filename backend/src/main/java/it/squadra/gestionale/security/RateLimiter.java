package it.squadra.gestionale.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Contatore a finestra fissa in memoria. Adatto a una singola istanza del backend:
 * con più istanze ogni nodo avrebbe i propri contatori (servirebbe un archivio condiviso, es. Redis).
 */
@Component
public class RateLimiter {

    /** Oltre questa soglia di chiavi si eliminano le finestre scadute, per non crescere senza limiti. */
    private static final int CLEANUP_THRESHOLD = 10_000;

    private final Map<String, Window> windows = new ConcurrentHashMap<>();
    private final Clock clock;

    public RateLimiter(Clock clock) {
        this.clock = clock;
    }

    public Decision tryAcquire(String key, int capacity, Duration windowLength) {
        Instant now = clock.instant();
        if (windows.size() > CLEANUP_THRESHOLD) {
            windows.values().removeIf(window -> window.isExpired(now));
        }
        Window window = windows.compute(key, (k, current) ->
                current == null || current.isExpired(now) ? new Window(now.plus(windowLength)) : current);
        synchronized (window) {
            if (window.count < capacity) {
                window.count++;
                return Decision.allow();
            }
            long retryAfter = Math.max(1, Duration.between(now, window.resetAt).toSeconds());
            return Decision.reject(retryAfter);
        }
    }

    private static final class Window {
        private final Instant resetAt;
        private int count;

        private Window(Instant resetAt) {
            this.resetAt = resetAt;
        }

        private boolean isExpired(Instant now) {
            return !resetAt.isAfter(now);
        }
    }

    public record Decision(boolean allowed, long retryAfterSeconds) {

        static Decision allow() {
            return new Decision(true, 0);
        }

        static Decision reject(long retryAfterSeconds) {
            return new Decision(false, retryAfterSeconds);
        }
    }
}

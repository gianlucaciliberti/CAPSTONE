package it.squadra.gestionale.security;

import it.squadra.gestionale.config.AppProperties.RateLimit;
import it.squadra.gestionale.config.AppProperties.RateLimit.Rule;
import it.squadra.gestionale.exceptions.ApiException.ErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Applica i limiti di {@code app.rate-limit} per IP del client. Oltre la soglia risponde
 * 429 Too Many Requests con header Retry-After, senza raggiungere i controller.
 * L'IP è quello reale anche dietro il proxy di Render grazie a {@code server.forward-headers-strategy}.
 */
public class RateLimitFilter extends OncePerRequestFilter {

    private final RateLimiter limiter;
    private final RateLimit properties;

    public RateLimitFilter(Clock clock, RateLimit properties) {
        this.limiter = new RateLimiter(clock);
        this.properties = properties;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !properties.enabled() || !"POST".equals(request.getMethod());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        for (Rule rule : properties.rules()) {
            if (rule.path().equals(path)) {
                RateLimiter.Decision decision = limiter.tryAcquire(
                        rule.path() + "|" + request.getRemoteAddr(), rule.capacity(), rule.window());
                if (!decision.allowed()) {
                    response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(decision.retryAfterSeconds()));
                    SecurityConfig.writeProblem(response, HttpStatus.TOO_MANY_REQUESTS, ErrorCode.TOO_MANY_REQUESTS,
                            "Troppe richieste. Attendi qualche minuto e riprova.");
                    return;
                }
            }
        }
        chain.doFilter(request, response);
    }

    /**
     * Contatore a finestra fissa in memoria. Adatto a una singola istanza del backend:
     * con più istanze ogni nodo avrebbe i propri contatori (servirebbe un archivio condiviso, es. Redis).
     */
    static class RateLimiter {

        /** Oltre questa soglia di chiavi si eliminano le finestre scadute, per non crescere senza limiti. */
        private static final int CLEANUP_THRESHOLD = 10_000;

        private final Map<String, Window> windows = new ConcurrentHashMap<>();
        private final Clock clock;

        RateLimiter(Clock clock) {
            this.clock = clock;
        }

        Decision tryAcquire(String key, int capacity, Duration windowLength) {
            Instant now = clock.instant();
            if (windows.size() > CLEANUP_THRESHOLD) {
                windows.values().removeIf(window -> window.isExpired(now));
            }
            Window window = windows.compute(key, (k, current) ->
                    current == null || current.isExpired(now) ? new Window(now.plus(windowLength)) : current);
            synchronized (window) {
                if (window.count < capacity) {
                    window.count++;
                    return new Decision(true, 0);
                }
                long retryAfter = Math.max(1, Duration.between(now, window.resetAt).toSeconds());
                return new Decision(false, retryAfter);
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

        record Decision(boolean allowed, long retryAfterSeconds) {
        }
    }
}

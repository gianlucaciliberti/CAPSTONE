package it.squadra.gestionale.security;

import it.squadra.gestionale.config.RateLimitProperties;
import it.squadra.gestionale.config.RateLimitProperties.Rule;
import it.squadra.gestionale.exceptions.ErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Applica i limiti di {@link RateLimitProperties} per IP del client. Oltre la soglia risponde
 * 429 Too Many Requests con header Retry-After, senza raggiungere i controller.
 * L'IP è quello reale anche dietro il proxy di Render grazie a {@code server.forward-headers-strategy}.
 */
public class RateLimitFilter extends OncePerRequestFilter {

    private final RateLimiter limiter;
    private final RateLimitProperties properties;

    public RateLimitFilter(RateLimiter limiter, RateLimitProperties properties) {
        this.limiter = limiter;
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
                    reject(response, decision.retryAfterSeconds());
                    return;
                }
            }
        }
        chain.doFilter(request, response);
    }

    private static void reject(HttpServletResponse response, long retryAfterSeconds) throws IOException {
        HttpStatus status = HttpStatus.TOO_MANY_REQUESTS;
        response.setStatus(status.value());
        response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfterSeconds));
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("""
                {"type":"about:blank","title":"%s","status":%d,"detail":"%s","code":"%s"}"""
                .formatted(status.getReasonPhrase(), status.value(),
                        "Troppe richieste. Attendi qualche minuto e riprova.", ErrorCode.TOO_MANY_REQUESTS.name()));
    }
}

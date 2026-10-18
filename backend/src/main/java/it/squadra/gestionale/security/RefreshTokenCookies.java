package it.squadra.gestionale.security;

import it.squadra.gestionale.config.AppProperties;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * Cookie del refresh token: HttpOnly (non leggibile da JavaScript), SameSite=Strict (non inviato da altri siti),
 * Secure in produzione, limitato al percorso degli endpoint di autenticazione.
 */
@Component
public class RefreshTokenCookies {

    public static final String COOKIE_NAME = "refresh_token";
    static final String COOKIE_PATH = "/api/v1/auth";

    private final boolean secure;
    private final Clock clock;

    public RefreshTokenCookies(AppProperties properties, Clock clock) {
        this.secure = properties.security().cookieSecure();
        this.clock = clock;
    }

    public void write(HttpServletResponse response, String token, Instant expiresAt) {
        Duration maxAge = Duration.between(clock.instant(), expiresAt);
        response.addHeader(HttpHeaders.SET_COOKIE, build(token, maxAge).toString());
    }

    public void clear(HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, build("", Duration.ZERO).toString());
    }

    private ResponseCookie build(String value, Duration maxAge) {
        return ResponseCookie.from(COOKIE_NAME, value)
                .httpOnly(true)
                .secure(secure)
                .sameSite("Strict")
                .path(COOKIE_PATH)
                .maxAge(maxAge)
                .build();
    }
}

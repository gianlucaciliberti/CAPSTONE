package it.squadra.gestionale.security;

import it.squadra.gestionale.config.AppProperties;
import it.squadra.gestionale.exceptions.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * Strumenti per i token opachi e per gli endpoint autenticati dal cookie (refresh e logout):
 * <ul>
 *   <li>generazione e hash dei token casuali (refresh token, link di reset password, verifica email);</li>
 *   <li>cookie del refresh token: HttpOnly, SameSite=Strict, Secure in produzione, limitato a /api/v1/auth;</li>
 *   <li>protezione CSRF: oltre a SameSite=Strict, le richieste devono avere l'header {@value #CSRF_HEADER},
 *       che un sito esterno non può aggiungere senza una preflight CORS rifiutata dal backend.</li>
 * </ul>
 */
@Component
public class AuthTokens {

    public static final String COOKIE_NAME = "refresh_token";
    static final String COOKIE_PATH = "/api/v1/auth";
    public static final String CSRF_HEADER = "X-Requested-With";
    public static final String CSRF_VALUE = "XMLHttpRequest";

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int TOKEN_BYTES = 32;

    private final boolean secureCookie;
    private final Clock clock;

    public AuthTokens(AppProperties properties, Clock clock) {
        this.secureCookie = properties.security().cookieSecure();
        this.clock = clock;
    }

    /** Nuovo token casuale a 256 bit, codificato Base64 URL-safe (adatto a cookie e link). */
    public static String generate() {
        byte[] bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * Hash SHA-256 in esadecimale (64 caratteri), come salvato nelle colonne token_hash.
     * SHA-256 senza salt è adeguato perché il token ha 256 bit di entropia: non è indovinabile come una password.
     */
    public static String hash(String rawToken) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 non disponibile", e);
        }
    }

    public void writeRefreshCookie(HttpServletResponse response, String token, Instant expiresAt) {
        Duration maxAge = Duration.between(clock.instant(), expiresAt);
        response.addHeader(HttpHeaders.SET_COOKIE, refreshCookie(token, maxAge).toString());
    }

    public void clearRefreshCookie(HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, refreshCookie("", Duration.ZERO).toString());
    }

    public void requireCsrfHeader(HttpServletRequest request) {
        if (!CSRF_VALUE.equals(request.getHeader(CSRF_HEADER))) {
            throw ApiException.forbidden("Richiesta non valida.");
        }
    }

    private ResponseCookie refreshCookie(String value, Duration maxAge) {
        return ResponseCookie.from(COOKIE_NAME, value)
                .httpOnly(true)
                .secure(secureCookie)
                .sameSite("Strict")
                .path(COOKIE_PATH)
                .maxAge(maxAge)
                .build();
    }
}

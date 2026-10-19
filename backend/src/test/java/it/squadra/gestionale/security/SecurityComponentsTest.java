package it.squadra.gestionale.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import it.squadra.gestionale.config.AppProperties;
import it.squadra.gestionale.entities.User;
import it.squadra.gestionale.support.MutableClock;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.test.util.ReflectionTestUtils;

/** Test unitari dei componenti di sicurezza (senza Spring e senza database). */
class SecurityComponentsTest {

    @Nested
    class JwtTokens {

        // Segreti fittizi usati solo nei test (oltre 32 byte, Base64).
        private static final String SECRET = "dGVzdC1vbmx5LXNlY3JldC1ub3QtZm9yLXByb2R1Y3Rpb24tdXNlLTEyMzQ1Ng==";
        private static final String OTHER_SECRET = "YW5vdGhlci10ZXN0LXNlY3JldC1ub3QtZm9yLXByb2R1Y3Rpb24tMTIzNDU2Nzg=";
        private static final Instant NOW = Instant.parse("2026-10-17T10:00:00Z");

        private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        private final SecurityConfig config = new SecurityConfig();

        @Test
        void issuedTokenContainsOnlyUserIdAndIsAcceptedByDecoder() {
            AppProperties properties = properties(SECRET, "gestionale-squadra");
            JwtService service = new JwtService(config.jwtEncoder(properties), clock, properties);

            JwtService.AccessToken token = service.issueAccessToken(user(42L));
            Jwt jwt = config.jwtDecoder(properties, clock).decode(token.value());

            assertThat(jwt.getSubject()).isEqualTo("42");
            assertThat(jwt.getClaimAsString("iss")).isEqualTo("gestionale-squadra");
            assertThat(jwt.getClaimAsString("token_type")).isEqualTo("access");
            assertThat(jwt.getClaims()).doesNotContainKeys("role", "email");
            assertThat(token.expiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(15)));
        }

        @Test
        void expiredTokenIsRejected() {
            AppProperties properties = properties(SECRET, "gestionale-squadra");
            String token = new JwtService(config.jwtEncoder(properties), clock, properties)
                    .issueAccessToken(user(1L)).value();

            Clock sixteenMinutesLater = Clock.offset(clock, Duration.ofMinutes(16));
            JwtDecoder decoder = config.jwtDecoder(properties, sixteenMinutesLater);

            assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtException.class);
        }

        @Test
        void tokenSignedWithAnotherKeyIsRejected() {
            AppProperties attacker = properties(OTHER_SECRET, "gestionale-squadra");
            String forged = new JwtService(config.jwtEncoder(attacker), clock, attacker)
                    .issueAccessToken(user(1L)).value();

            JwtDecoder decoder = config.jwtDecoder(properties(SECRET, "gestionale-squadra"), clock);

            assertThatThrownBy(() -> decoder.decode(forged)).isInstanceOf(JwtException.class);
        }

        @Test
        void tamperedPayloadIsRejected() {
            AppProperties properties = properties(SECRET, "gestionale-squadra");
            String token = new JwtService(config.jwtEncoder(properties), clock, properties)
                    .issueAccessToken(user(1L)).value();
            String[] parts = token.split("\\.");
            String otherPayload = new JwtService(config.jwtEncoder(properties), clock, properties)
                    .issueAccessToken(user(999L)).value().split("\\.")[1];
            String tampered = parts[0] + "." + otherPayload + "." + parts[2];

            JwtDecoder decoder = config.jwtDecoder(properties, clock);

            assertThatThrownBy(() -> decoder.decode(tampered)).isInstanceOf(JwtException.class);
        }

        @Test
        void tokenFromAnotherIssuerIsRejected() {
            AppProperties otherApp = properties(SECRET, "altra-applicazione");
            String token = new JwtService(config.jwtEncoder(otherApp), clock, otherApp)
                    .issueAccessToken(user(1L)).value();

            JwtDecoder decoder = config.jwtDecoder(properties(SECRET, "gestionale-squadra"), clock);

            assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtException.class);
        }

        @Test
        void missingOrShortSecretPreventsStartup() {
            assertThatThrownBy(() -> config.jwtEncoder(properties("", "gestionale-squadra")))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("JWT_SECRET non configurato");
            assertThatThrownBy(() -> config.jwtEncoder(properties("c2hvcnQ=", "gestionale-squadra")))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("troppo corto");
        }

        private static User user(long id) {
            User user = new User("Mario", "Rossi", "mario@example.com", "hash");
            ReflectionTestUtils.setField(user, "id", id);
            return user;
        }

        private static AppProperties properties(String secret, String issuer) {
            return new AppProperties(
                    ZoneId.of("Europe/Rome"),
                    "http://localhost:5173",
                    new AppProperties.Cors(List.of("http://localhost:5173")),
                    new AppProperties.Security(
                            new AppProperties.Jwt(secret, issuer, Duration.ofMinutes(15), Duration.ofDays(14)), false),
                    new AppProperties.RateLimit(false, List.of()),
                    new AppProperties.BootstrapAdmin(null, null, null, null),
                    new AppProperties.Mail(AppProperties.MailMode.LOG, "noreply@example.com", "Test", 5, false,
                            new AppProperties.Mailjet(null, null, null)),
                    new AppProperties.OpenAi(false, null, null, Duration.ofSeconds(20)));
        }
    }

    @Nested
    class OpaqueTokens {

        @Test
        void generatesUrlSafeUniqueTokensOf256Bits() {
            Set<String> seen = new HashSet<>();
            for (int i = 0; i < 1000; i++) {
                String token = AuthTokens.generate();
                assertThat(token).hasSize(43).matches("[A-Za-z0-9_-]+");
                assertThat(seen.add(token)).isTrue();
            }
        }

        @Test
        void hashIsDeterministicSha256Hex() {
            assertThat(AuthTokens.hash("abc"))
                    .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
            assertThat(AuthTokens.hash("abc")).isEqualTo(AuthTokens.hash("abc"));
        }
    }

    @Nested
    class RateLimiting {

        private final MutableClock clock = new MutableClock(Instant.parse("2026-10-19T10:00:00Z"));
        private final RateLimitFilter.RateLimiter limiter = new RateLimitFilter.RateLimiter(clock);

        @Test
        void allowsUpToCapacityThenRejectsWithRetryAfter() {
            for (int i = 0; i < 3; i++) {
                assertThat(limiter.tryAcquire("login|1.2.3.4", 3, Duration.ofMinutes(1)).allowed()).isTrue();
            }
            clock.advance(Duration.ofSeconds(20));

            RateLimitFilter.RateLimiter.Decision rejected = limiter.tryAcquire("login|1.2.3.4", 3, Duration.ofMinutes(1));

            assertThat(rejected.allowed()).isFalse();
            assertThat(rejected.retryAfterSeconds()).isEqualTo(40);
        }

        @Test
        void newWindowStartsAfterExpiry() {
            for (int i = 0; i < 3; i++) {
                limiter.tryAcquire("login|1.2.3.4", 3, Duration.ofMinutes(1));
            }
            clock.advance(Duration.ofMinutes(1));

            assertThat(limiter.tryAcquire("login|1.2.3.4", 3, Duration.ofMinutes(1)).allowed()).isTrue();
        }

        @Test
        void keysAreIndependent() {
            for (int i = 0; i < 3; i++) {
                limiter.tryAcquire("login|1.2.3.4", 3, Duration.ofMinutes(1));
            }

            assertThat(limiter.tryAcquire("login|5.6.7.8", 3, Duration.ofMinutes(1)).allowed()).isTrue();
            assertThat(limiter.tryAcquire("register|1.2.3.4", 3, Duration.ofMinutes(1)).allowed()).isTrue();
        }
    }
}

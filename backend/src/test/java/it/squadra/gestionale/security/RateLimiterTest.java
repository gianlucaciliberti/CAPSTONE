package it.squadra.gestionale.security;

import static org.assertj.core.api.Assertions.assertThat;

import it.squadra.gestionale.support.MutableClock;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class RateLimiterTest {

    private final MutableClock clock = new MutableClock(Instant.parse("2026-10-19T10:00:00Z"));
    private final RateLimiter limiter = new RateLimiter(clock);

    @Test
    void allowsUpToCapacityThenRejectsWithRetryAfter() {
        for (int i = 0; i < 3; i++) {
            assertThat(limiter.tryAcquire("login|1.2.3.4", 3, Duration.ofMinutes(1)).allowed()).isTrue();
        }
        clock.advance(Duration.ofSeconds(20));

        RateLimiter.Decision rejected = limiter.tryAcquire("login|1.2.3.4", 3, Duration.ofMinutes(1));

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

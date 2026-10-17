package it.squadra.gestionale.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import it.squadra.gestionale.config.AppProperties;
import it.squadra.gestionale.entities.RefreshToken;
import it.squadra.gestionale.entities.User;
import it.squadra.gestionale.enums.AccountStatus;
import it.squadra.gestionale.exceptions.ApiException;
import it.squadra.gestionale.repositories.RefreshTokenRepository;
import it.squadra.gestionale.repositories.UserRepository;
import it.squadra.gestionale.security.OpaqueTokens;
import it.squadra.gestionale.services.RefreshTokenService.IssuedRefreshToken;
import it.squadra.gestionale.support.IntegrationTest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;

@IntegrationTest
class RefreshTokenServiceIntegrationTest {

    @Autowired
    private RefreshTokenService service;
    @Autowired
    private RefreshTokenRepository tokens;
    @Autowired
    private UserRepository users;
    @Autowired
    private Clock clock;
    @Autowired
    private AppProperties properties;
    @Autowired
    private PlatformTransactionManager transactionManager;

    private User user;

    @BeforeEach
    void createActiveUser() {
        user = new User("Mario", "Rossi", "mario.refresh@example.com", "hash");
        user.changeStatus(AccountStatus.ACTIVE, null, Instant.now());
        user = users.save(user);
    }

    @Test
    void createSessionStoresOnlyTheHash() {
        IssuedRefreshToken issued = service.createSession(user, "JUnit", "127.0.0.1");

        RefreshToken stored = tokens.findByTokenHash(OpaqueTokens.hash(issued.value())).orElseThrow();
        assertThat(stored.getTokenHash()).hasSize(64).isNotEqualTo(issued.value());
        assertThat(stored.getUser().getId()).isEqualTo(user.getId());
        assertThat(issued.expiresAt()).isEqualTo(stored.getExpiresAt());
    }

    @Test
    void rotateRevokesOldTokenAndIssuesNewOneInSameFamily() {
        IssuedRefreshToken first = service.createSession(user, "JUnit", "127.0.0.1");

        IssuedRefreshToken second = service.rotate(first.value(), "JUnit", "127.0.0.1");

        RefreshToken old = tokens.findByTokenHash(OpaqueTokens.hash(first.value())).orElseThrow();
        RefreshToken current = tokens.findByTokenHash(OpaqueTokens.hash(second.value())).orElseThrow();
        assertThat(second.value()).isNotEqualTo(first.value());
        assertThat(old.wasRotated()).isTrue();
        assertThat(old.getReplacedBy().getId()).isEqualTo(current.getId());
        assertThat(current.getFamilyId()).isEqualTo(old.getFamilyId());
        assertThat(current.getRevokedAt()).isNull();
    }

    @Test
    void reusingRotatedTokenRevokesWholeFamilyWithoutRollingBack() {
        IssuedRefreshToken first = service.createSession(user, "JUnit", "127.0.0.1");
        IssuedRefreshToken second = service.rotate(first.value(), "JUnit", "127.0.0.1");

        assertThatThrownBy(() -> service.rotate(first.value(), "Attaccante", "10.0.0.1"))
                .isInstanceOf(ApiException.class)
                .extracting("status").isEqualTo(HttpStatus.UNAUTHORIZED);

        // La revoca deve sopravvivere all'eccezione: la transazione non è marcata per il rollback.
        TransactionStatus participating = transactionManager.getTransaction(new DefaultTransactionDefinition());
        assertThat(participating.isRollbackOnly()).isFalse();
        RefreshToken current = tokens.findByTokenHash(OpaqueTokens.hash(second.value())).orElseThrow();
        assertThat(current.getRevokedReason()).isEqualTo(RefreshTokenService.REASON_REUSE_DETECTED);
        assertThatThrownBy(() -> service.rotate(second.value(), "JUnit", "127.0.0.1"))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void expiredTokenIsRejected() {
        IssuedRefreshToken issued = service.createSession(user, "JUnit", "127.0.0.1");
        Clock afterExpiry = Clock.offset(clock, properties.security().jwt().refreshTokenTtl().plus(Duration.ofMinutes(1)));
        RefreshTokenService later = new RefreshTokenService(tokens, afterExpiry, properties);

        assertThatThrownBy(() -> later.rotate(issued.value(), "JUnit", "127.0.0.1"))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void unknownOrMissingTokenIsRejected() {
        assertThatThrownBy(() -> service.rotate("token-inventato", null, null)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> service.rotate(null, null, null)).isInstanceOf(ApiException.class);
    }

    @Test
    void suspendedUserCannotRenewSession() {
        IssuedRefreshToken issued = service.createSession(user, "JUnit", "127.0.0.1");
        user.changeStatus(AccountStatus.SUSPENDED, null, Instant.now());
        users.saveAndFlush(user);

        assertThatThrownBy(() -> service.rotate(issued.value(), "JUnit", "127.0.0.1"))
                .isInstanceOf(ApiException.class);
        RefreshToken stored = tokens.findByTokenHash(OpaqueTokens.hash(issued.value())).orElseThrow();
        assertThat(stored.getRevokedReason()).isEqualTo(RefreshTokenService.REASON_ACCOUNT_INACTIVE);
    }

    @Test
    void logoutRevokesSessionAndIsIdempotent() {
        IssuedRefreshToken issued = service.createSession(user, "JUnit", "127.0.0.1");

        service.revoke(issued.value());
        service.revoke(issued.value());
        service.revoke("token-inventato");
        service.revoke(null);

        assertThatThrownBy(() -> service.rotate(issued.value(), "JUnit", "127.0.0.1"))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void revokeAllForUserClosesEverySession() {
        IssuedRefreshToken phone = service.createSession(user, "Telefono", "127.0.0.1");
        IssuedRefreshToken laptop = service.createSession(user, "Portatile", "127.0.0.1");

        int revoked = service.revokeAllForUser(user.getId(), "PASSWORD_CHANGED");

        assertThat(revoked).isEqualTo(2);
        for (String raw : List.of(phone.value(), laptop.value())) {
            assertThatThrownBy(() -> service.rotate(raw, "JUnit", "127.0.0.1")).isInstanceOf(ApiException.class);
        }
    }
}

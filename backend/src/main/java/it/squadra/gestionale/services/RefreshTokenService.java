package it.squadra.gestionale.services;

import it.squadra.gestionale.config.AppProperties;
import it.squadra.gestionale.entities.RefreshToken;
import it.squadra.gestionale.entities.User;
import it.squadra.gestionale.exceptions.ApiException;
import it.squadra.gestionale.repositories.RefreshTokenRepository;
import it.squadra.gestionale.security.AuthTokens;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sessioni basate su refresh token opachi con rotazione.
 * <ul>
 *   <li>Ogni login crea una nuova "famiglia" (sessione).</li>
 *   <li>Ogni rinnovo revoca il token usato e ne emette uno nuovo nella stessa famiglia.</li>
 *   <li>Il riuso di un token già ruotato indica un probabile furto: l'intera famiglia viene revocata.</li>
 * </ul>
 */
@Service
public class RefreshTokenService {

    public static final String REASON_LOGOUT = "LOGOUT";
    public static final String REASON_REUSE_DETECTED = "REUSE_DETECTED";
    public static final String REASON_ACCOUNT_INACTIVE = "ACCOUNT_INACTIVE";

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenService.class);
    private static final String INVALID_SESSION = "Sessione scaduta o non valida. Effettua di nuovo l'accesso.";

    private final RefreshTokenRepository repository;
    private final Clock clock;
    private final Duration ttl;

    public RefreshTokenService(RefreshTokenRepository repository, Clock clock, AppProperties properties) {
        this.repository = repository;
        this.clock = clock;
        this.ttl = properties.security().jwt().refreshTokenTtl();
    }

    /** Nuova sessione dopo un login riuscito. */
    @Transactional
    public IssuedRefreshToken createSession(User user, String userAgent, String ipAddress) {
        return issue(user, UUID.randomUUID(), userAgent, ipAddress).issued();
    }

    /**
     * Rinnova la sessione: il token ricevuto viene revocato e sostituito.
     * {@code noRollbackFor}: la revoca della famiglia in caso di riuso deve essere salvata anche se la richiesta fallisce.
     */
    @Transactional(noRollbackFor = ApiException.class)
    public IssuedRefreshToken rotate(String rawToken, String userAgent, String ipAddress) {
        RefreshToken current = findLocked(rawToken);
        Instant now = clock.instant();

        if (current.getRevokedAt() != null) {
            if (current.wasRotated()) {
                log.warn("Riuso di un refresh token già ruotato (utente {}, famiglia {}): sessione revocata",
                        current.getUser().getId(), current.getFamilyId());
                repository.revokeFamily(current.getFamilyId(), now, REASON_REUSE_DETECTED);
            }
            throw ApiException.unauthenticated(INVALID_SESSION);
        }
        if (!current.getExpiresAt().isAfter(now)) {
            throw ApiException.unauthenticated(INVALID_SESSION);
        }
        if (!current.getUser().isActive()) {
            repository.revokeFamily(current.getFamilyId(), now, REASON_ACCOUNT_INACTIVE);
            throw ApiException.unauthenticated(INVALID_SESSION);
        }

        Issued next = issue(current.getUser(), current.getFamilyId(), userAgent, ipAddress);
        current.replaceWith(next.entity(), now);
        return next.issued();
    }

    /** Logout della sessione corrente. Idempotente: un token sconosciuto o già revocato non genera errori. */
    @Transactional
    public void revoke(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return;
        }
        repository.findByTokenHash(AuthTokens.hash(rawToken))
                .ifPresent(token -> repository.revokeFamily(token.getFamilyId(), clock.instant(), REASON_LOGOUT));
    }

    /** Chiude tutte le sessioni dell'utente (cambio password, sospensione, rifiuto). */
    @Transactional
    public int revokeAllForUser(Long userId, String reason) {
        return repository.revokeAllForUser(userId, clock.instant(), reason);
    }

    private RefreshToken findLocked(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw ApiException.unauthenticated(INVALID_SESSION);
        }
        return repository.findForUpdateByTokenHash(AuthTokens.hash(rawToken))
                .orElseThrow(() -> ApiException.unauthenticated(INVALID_SESSION));
    }

    private Issued issue(User user, UUID familyId, String userAgent, String ipAddress) {
        String raw = AuthTokens.generate();
        Instant now = clock.instant();
        Instant expiresAt = now.plus(ttl);
        RefreshToken entity = repository.save(new RefreshToken(user, AuthTokens.hash(raw), familyId, now, expiresAt,
                truncate(userAgent, 255), truncate(ipAddress, 45)));
        return new Issued(entity, new IssuedRefreshToken(raw, expiresAt, user));
    }

    private static String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }

    /** Token in chiaro da inviare al client (nel cookie) e utente proprietario. */
    public record IssuedRefreshToken(String value, Instant expiresAt, User user) {
    }

    private record Issued(RefreshToken entity, IssuedRefreshToken issued) {
    }
}

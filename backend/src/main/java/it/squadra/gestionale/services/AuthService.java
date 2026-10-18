package it.squadra.gestionale.services;

import it.squadra.gestionale.entities.User;
import it.squadra.gestionale.exceptions.ApiException;
import it.squadra.gestionale.exceptions.ErrorCode;
import it.squadra.gestionale.repositories.UserRepository;
import it.squadra.gestionale.security.JwtService;
import it.squadra.gestionale.security.JwtService.AccessToken;
import it.squadra.gestionale.security.OpaqueTokens;
import it.squadra.gestionale.services.RefreshTokenService.IssuedRefreshToken;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Login con email e password.
 * <ul>
 *   <li>Email inesistente e password errata producono lo stesso errore (nessuna enumerazione degli utenti).</li>
 *   <li>Dopo {@value #MAX_FAILED_ATTEMPTS} tentativi falliti l'account resta bloccato per 15 minuti.</li>
 *   <li>Solo gli account ACTIVE ottengono i token; lo stato viene comunicato solo dopo una password corretta.</li>
 * </ul>
 */
@Service
public class AuthService {

    static final int MAX_FAILED_ATTEMPTS = 5;
    static final Duration LOCK_DURATION = Duration.ofMinutes(15);
    static final String INVALID_CREDENTIALS = "Email o password non corretti.";

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final Clock clock;
    /** Hash fittizio: per le email inesistenti si esegue comunque un confronto BCrypt, con tempi di risposta simili. */
    private final String dummyHash;

    public AuthService(UserRepository users, PasswordEncoder passwordEncoder, JwtService jwtService,
                       RefreshTokenService refreshTokenService, Clock clock) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
        this.clock = clock;
        this.dummyHash = passwordEncoder.encode(OpaqueTokens.generate());
    }

    /** {@code noRollbackFor}: il contatore dei tentativi falliti deve essere salvato anche quando il login fallisce. */
    @Transactional(noRollbackFor = ApiException.class)
    public LoginResult login(String email, String password, String userAgent, String ipAddress) {
        Instant now = clock.instant();
        Optional<User> found = users.findByEmail(User.normalizeEmail(email));
        if (found.isEmpty()) {
            passwordEncoder.matches(password, dummyHash);
            throw ApiException.unauthenticated(INVALID_CREDENTIALS);
        }
        User user = found.get();

        if (user.isLocked(now)) {
            throw new ApiException(HttpStatus.LOCKED, ErrorCode.ACCOUNT_LOCKED,
                    "Account temporaneamente bloccato per troppi tentativi. Riprova tra qualche minuto.");
        }
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            user.registerFailedLogin(now, MAX_FAILED_ATTEMPTS, LOCK_DURATION);
            if (user.isLocked(now)) {
                log.warn("Account {} bloccato per troppi tentativi di login falliti", user.getId());
            }
            throw ApiException.unauthenticated(INVALID_CREDENTIALS);
        }

        user.registerSuccessfulLogin(now);
        ensureActive(user);

        IssuedRefreshToken refreshToken = refreshTokenService.createSession(user, userAgent, ipAddress);
        AccessToken accessToken = jwtService.issueAccessToken(user);
        return new LoginResult(accessToken, refreshToken, user);
    }

    private static void ensureActive(User user) {
        switch (user.getStatus()) {
            case ACTIVE -> {
            }
            case PENDING -> throw new ApiException(HttpStatus.FORBIDDEN, ErrorCode.ACCOUNT_PENDING,
                    "Il tuo account è in attesa di approvazione da parte di un amministratore.");
            case REJECTED -> throw new ApiException(HttpStatus.FORBIDDEN, ErrorCode.ACCOUNT_REJECTED,
                    "La tua richiesta di registrazione non è stata approvata.");
            case SUSPENDED -> throw new ApiException(HttpStatus.FORBIDDEN, ErrorCode.ACCOUNT_SUSPENDED,
                    "Il tuo account è sospeso. Contatta un amministratore.");
        }
    }

    public record LoginResult(AccessToken accessToken, IssuedRefreshToken refreshToken, User user) {
    }
}

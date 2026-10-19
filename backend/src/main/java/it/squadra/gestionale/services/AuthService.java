package it.squadra.gestionale.services;

import it.squadra.gestionale.config.AppProperties;
import it.squadra.gestionale.dto.AuthDto.RegisterRequest;
import it.squadra.gestionale.entities.AuditLog.AuditAction;
import it.squadra.gestionale.entities.EmailNotification.NotificationType;
import it.squadra.gestionale.entities.User;
import it.squadra.gestionale.entities.User.AccountStatus;
import it.squadra.gestionale.entities.UserToken;
import it.squadra.gestionale.entities.UserToken.TokenPurpose;
import it.squadra.gestionale.exceptions.ApiException;
import it.squadra.gestionale.exceptions.ApiException.ErrorCode;
import it.squadra.gestionale.repositories.UserRepository;
import it.squadra.gestionale.repositories.UserTokenRepository;
import it.squadra.gestionale.security.AuthTokens;
import it.squadra.gestionale.security.JwtService;
import it.squadra.gestionale.security.JwtService.AccessToken;
import it.squadra.gestionale.services.EmailService.EmailMessage;
import it.squadra.gestionale.services.RefreshTokenService.IssuedRefreshToken;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.HtmlUtils;

/**
 * Registrazione, login, rinnovo, logout e reimpostazione della password.
 * <ul>
 *   <li>La registrazione crea sempre un account PLAYER/PENDING, da approvare da parte di un Admin.</li>
 *   <li>Email già registrata, email inesistente e password errata non rivelano mai quali utenti esistono.</li>
 *   <li>Dopo {@value #MAX_FAILED_ATTEMPTS} tentativi falliti l'account resta bloccato per 15 minuti.</li>
 *   <li>Solo gli account ACTIVE ottengono i token; lo stato viene comunicato solo dopo una password corretta.</li>
 * </ul>
 */
@Service
public class AuthService {

    static final int MAX_FAILED_ATTEMPTS = 5;
    static final Duration LOCK_DURATION = Duration.ofMinutes(15);
    static final String INVALID_CREDENTIALS = "Email o password non corretti.";
    static final Duration RESET_TOKEN_TTL = Duration.ofMinutes(30);
    /** Intervallo minimo tra due email di reset allo stesso utente (contro l'invio massivo di email). */
    static final Duration RESET_REQUEST_COOLDOWN = Duration.ofMinutes(2);

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository users;
    private final UserTokenRepository userTokens;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final AuditService auditService;
    private final EmailService emailService;
    private final Clock clock;
    private final String frontendUrl;
    /** Hash fittizio: per le email inesistenti si esegue comunque un confronto BCrypt, con tempi di risposta simili. */
    private final String dummyHash;

    public AuthService(UserRepository users, UserTokenRepository userTokens, PasswordEncoder passwordEncoder,
                       JwtService jwtService, RefreshTokenService refreshTokenService, AuditService auditService,
                       EmailService emailService, Clock clock, AppProperties properties) {
        this.users = users;
        this.userTokens = userTokens;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
        this.auditService = auditService;
        this.emailService = emailService;
        this.clock = clock;
        this.frontendUrl = properties.frontendUrl().replaceAll("/+$", "");
        this.dummyHash = passwordEncoder.encode(AuthTokens.generate());
    }

    /** Registrazione pubblica. Se l'email è già registrata non si crea nulla e non si segnala nulla al chiamante. */
    @Transactional
    public void register(RegisterRequest request, String ipAddress) {
        String email = User.normalizeEmail(request.email());
        // L'hash si calcola sempre, anche per email già registrate: il tempo di risposta non rivela se l'account esiste.
        String passwordHash = passwordEncoder.encode(request.password());

        if (users.existsByEmail(email)) {
            log.info("Tentativo di registrazione con email già esistente");
            return;
        }

        User user = users.save(new User(request.firstName().trim(), request.lastName().trim(), email, passwordHash));
        auditService.record(user, AuditAction.USER_REGISTERED, "USER", user.getId(),
                "Nuova registrazione in attesa di approvazione",
                Map.of("role", user.getRole().name(), "status", user.getStatus().name()), ipAddress);
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

    /**
     * Rinnovo della sessione dal cookie: nuovo refresh token (rotazione) e nuovo access token.
     * {@code noRollbackFor}: una revoca per riuso o account inattivo deve restare salvata.
     */
    @Transactional(noRollbackFor = ApiException.class)
    public LoginResult refresh(String rawRefreshToken, String userAgent, String ipAddress) {
        IssuedRefreshToken refreshToken = refreshTokenService.rotate(rawRefreshToken, userAgent, ipAddress);
        AccessToken accessToken = jwtService.issueAccessToken(refreshToken.user());
        return new LoginResult(accessToken, refreshToken, refreshToken.user());
    }

    /** Logout della sessione legata al cookie. Idempotente. */
    @Transactional
    public void logout(String rawRefreshToken) {
        refreshTokenService.revoke(rawRefreshToken);
    }

    /**
     * Richiesta di reset password. Il chiamante riceve sempre la stessa risposta: l'email parte solo se
     * l'account esiste, non è sospeso/rifiutato e non ha già ricevuto un link negli ultimi minuti.
     */
    @Transactional
    public void requestPasswordReset(String rawEmail, String ipAddress) {
        Optional<User> found = users.findByEmail(User.normalizeEmail(rawEmail));
        if (found.isEmpty()) {
            return;
        }
        User user = found.get();
        if (user.getStatus() == AccountStatus.SUSPENDED || user.getStatus() == AccountStatus.REJECTED) {
            log.info("Reset password ignorato per account {} in stato {}", user.getId(), user.getStatus());
            return;
        }
        Instant now = clock.instant();
        if (userTokens.existsByUserIdAndPurposeAndCreatedAtAfter(user.getId(), TokenPurpose.PASSWORD_RESET,
                now.minus(RESET_REQUEST_COOLDOWN))) {
            log.info("Reset password già richiesto di recente per l'account {}: nessuna nuova email", user.getId());
            return;
        }

        // Un solo link valido alla volta: quelli precedenti smettono di funzionare.
        userTokens.invalidateActive(user.getId(), TokenPurpose.PASSWORD_RESET, now);
        String rawToken = AuthTokens.generate();
        UserToken token = userTokens.save(new UserToken(user, TokenPurpose.PASSWORD_RESET, AuthTokens.hash(rawToken),
                now, now.plus(RESET_TOKEN_TTL), ipAddress));

        String link = frontendUrl + "/reset-password?token=" + rawToken;
        emailService.enqueue(new EmailMessage(NotificationType.PASSWORD_RESET, user,
                "Reimposta la tua password",
                """
                Ciao %s,

                abbiamo ricevuto una richiesta di reimpostazione della password del tuo account.
                Per scegliere una nuova password apri questo link (valido %d minuti, utilizzabile una sola volta):

                %s

                Se non hai fatto tu la richiesta, ignora questa email: la tua password resta invariata.
                """.formatted(user.getFirstName(), RESET_TOKEN_TTL.toMinutes(), link),
                """
                <p>Ciao %s,</p>
                <p>abbiamo ricevuto una richiesta di reimpostazione della password del tuo account.</p>
                <p><a href="%s">Scegli una nuova password</a> (link valido %d minuti, utilizzabile una sola volta).</p>
                <p>Se non hai fatto tu la richiesta, ignora questa email: la tua password resta invariata.</p>
                """.formatted(HtmlUtils.htmlEscape(user.getFirstName()), HtmlUtils.htmlEscape(link),
                        RESET_TOKEN_TTL.toMinutes()),
                "USER", String.valueOf(user.getId()), "PASSWORD_RESET:" + token.getId()));
        auditService.record(user, AuditAction.PASSWORD_RESET_REQUESTED, "USER", user.getId(),
                "Richiesto link di reimpostazione password", null, ipAddress);
    }

    /**
     * Reimposta la password con un link monouso. Effetti: link consumato, password aggiornata, blocco per
     * tentativi falliti azzerato, email considerata verificata (il link è arrivato a quell'indirizzo),
     * tutte le sessioni dell'utente chiuse.
     */
    @Transactional
    public void resetPassword(String rawToken, String newPassword, String ipAddress) {
        Instant now = clock.instant();
        UserToken token = userTokens.findForUpdate(AuthTokens.hash(rawToken), TokenPurpose.PASSWORD_RESET)
                .filter(t -> t.isUsable(now))
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_TOKEN,
                        "Il link non è valido o è scaduto. Richiedi un nuovo link di reimpostazione."));
        User user = token.getUser();
        if (user.getStatus() == AccountStatus.SUSPENDED || user.getStatus() == AccountStatus.REJECTED) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_TOKEN,
                    "Il link non è valido o è scaduto. Richiedi un nuovo link di reimpostazione.");
        }

        token.markUsed(now);
        userTokens.invalidateActive(user.getId(), TokenPurpose.PASSWORD_RESET, now);
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        if (user.getEmailVerifiedAt() == null) {
            user.setEmailVerifiedAt(now);
        }
        int closedSessions = refreshTokenService.revokeAllForUser(user.getId(), "PASSWORD_RESET");
        auditService.record(user, AuditAction.PASSWORD_RESET, "USER", user.getId(),
                "Password reimpostata tramite link", Map.of("closedSessions", closedSessions), ipAddress);
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

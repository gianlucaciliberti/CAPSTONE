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

/**
 * Registrazione, verifica email, login, rinnovo, logout, cambio e reimpostazione della password.
 * <ul>
 *   <li>La registrazione crea sempre un account PLAYER/PENDING e invia il link di verifica dell'email.</li>
 *   <li>Email già registrata, email inesistente e password errata non rivelano mai quali utenti esistono.</li>
 *   <li>Dopo {@value #MAX_FAILED_ATTEMPTS} tentativi falliti l'account resta bloccato per 15 minuti.</li>
 *   <li>Solo gli account ACTIVE ottengono i token; lo stato viene comunicato solo dopo una password corretta.</li>
 *   <li>I link via email sono monouso, a scadenza, salvati solo come hash; un nuovo link invalida i precedenti.</li>
 * </ul>
 */
@Service
public class AuthService {

    static final int MAX_FAILED_ATTEMPTS = 5;
    static final Duration LOCK_DURATION = Duration.ofMinutes(15);
    static final String INVALID_CREDENTIALS = "Email o password non corretti.";
    static final Duration RESET_TOKEN_TTL = Duration.ofMinutes(30);
    static final Duration VERIFICATION_TOKEN_TTL = Duration.ofHours(24);
    /** Intervallo minimo tra due email con link allo stesso utente (contro l'invio massivo di email). */
    static final Duration LINK_REQUEST_COOLDOWN = Duration.ofMinutes(2);

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final String INVALID_LINK = "Il link non è valido o è scaduto. Richiedine uno nuovo.";

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

    // --- Registrazione e verifica email ---

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
        sendVerificationEmail(user, ipAddress);
    }

    /** Conferma l'indirizzo email con il link ricevuto. */
    @Transactional
    public void verifyEmail(String rawToken, String ipAddress) {
        Instant now = clock.instant();
        UserToken token = consumeToken(rawToken, TokenPurpose.EMAIL_VERIFICATION, now);
        User user = token.getUser();
        if (user.getEmailVerifiedAt() == null) {
            user.setEmailVerifiedAt(now);
            auditService.record(user, AuditAction.EMAIL_VERIFIED, "USER", user.getId(),
                    "Indirizzo email verificato", null, ipAddress);
        }
    }

    /** Nuovo link di verifica. Risposta sempre identica: l'email parte solo se ha senso inviarla. */
    @Transactional
    public void resendVerification(String rawEmail, String ipAddress) {
        users.findByEmail(User.normalizeEmail(rawEmail))
                .filter(user -> user.getEmailVerifiedAt() == null)
                .filter(user -> !isBlocked(user))
                .filter(user -> !recentlySent(user, TokenPurpose.EMAIL_VERIFICATION))
                .ifPresent(user -> sendVerificationEmail(user, ipAddress));
    }

    private void sendVerificationEmail(User user, String ipAddress) {
        IssuedLink link = issueLink(user, TokenPurpose.EMAIL_VERIFICATION, VERIFICATION_TOKEN_TTL,
                "/verify-email", ipAddress);
        emailService.enqueueTemplate(NotificationType.EMAIL_VERIFICATION, user, "Conferma il tuo indirizzo email",
                "email-verification", Map.of("link", link.url()), "USER", String.valueOf(user.getId()),
                "EMAIL_VERIFICATION:" + link.tokenId());
    }

    // --- Login, rinnovo, logout ---

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
        return newSession(user, userAgent, ipAddress);
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

    // --- Password ---

    /**
     * Cambio password dell'utente autenticato. Tutte le sessioni (anche su altri dispositivi) vengono chiuse
     * e ne viene aperta una nuova per il dispositivo corrente.
     */
    @Transactional
    public LoginResult changePassword(Long userId, String currentPassword, String newPassword, String userAgent,
                                      String ipAddress) {
        User user = users.findById(userId).orElseThrow(() -> ApiException.notFound("Utente non trovato."));
        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED,
                    "La password attuale non è corretta.");
        }
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        int closedSessions = refreshTokenService.revokeAllForUser(user.getId(), "PASSWORD_CHANGED");
        auditService.record(user, AuditAction.PASSWORD_CHANGED, "USER", user.getId(),
                "Password modificata dall'utente", Map.of("closedSessions", closedSessions), ipAddress);
        return newSession(user, userAgent, ipAddress);
    }

    /**
     * Richiesta di reset password. Il chiamante riceve sempre la stessa risposta: l'email parte solo se
     * l'account esiste, non è sospeso/rifiutato e non ha già ricevuto un link negli ultimi minuti.
     */
    @Transactional
    public void requestPasswordReset(String rawEmail, String ipAddress) {
        Optional<User> found = users.findByEmail(User.normalizeEmail(rawEmail))
                .filter(user -> !isBlocked(user))
                .filter(user -> !recentlySent(user, TokenPurpose.PASSWORD_RESET));
        if (found.isEmpty()) {
            return;
        }
        User user = found.get();
        IssuedLink link = issueLink(user, TokenPurpose.PASSWORD_RESET, RESET_TOKEN_TTL, "/reset-password", ipAddress);
        emailService.enqueueTemplate(NotificationType.PASSWORD_RESET, user, "Reimposta la tua password",
                "password-reset", Map.of("link", link.url(), "minutes", RESET_TOKEN_TTL.toMinutes()),
                "USER", String.valueOf(user.getId()), "PASSWORD_RESET:" + link.tokenId());
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
        UserToken token = consumeToken(rawToken, TokenPurpose.PASSWORD_RESET, now);
        User user = token.getUser();
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

    // --- Supporto ---

    private LoginResult newSession(User user, String userAgent, String ipAddress) {
        IssuedRefreshToken refreshToken = refreshTokenService.createSession(user, userAgent, ipAddress);
        AccessToken accessToken = jwtService.issueAccessToken(user);
        return new LoginResult(accessToken, refreshToken, user);
    }

    /** Crea un nuovo token monouso (invalidando i precedenti dello stesso tipo) e il link del frontend. */
    private IssuedLink issueLink(User user, TokenPurpose purpose, Duration ttl, String path, String ipAddress) {
        Instant now = clock.instant();
        userTokens.invalidateActive(user.getId(), purpose, now);
        String rawToken = AuthTokens.generate();
        UserToken token = userTokens.save(new UserToken(user, purpose, AuthTokens.hash(rawToken), now,
                now.plus(ttl), ipAddress));
        return new IssuedLink(token.getId(), frontendUrl + path + "?token=" + rawToken);
    }

    /** Verifica e consuma un token monouso (con lock di riga: lo stesso link non può essere usato due volte). */
    private UserToken consumeToken(String rawToken, TokenPurpose purpose, Instant now) {
        UserToken token = userTokens.findForUpdate(AuthTokens.hash(rawToken), purpose)
                .filter(t -> t.isUsable(now))
                .filter(t -> !isBlocked(t.getUser()))
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_TOKEN, INVALID_LINK));
        token.markUsed(now);
        userTokens.invalidateActive(token.getUser().getId(), purpose, now);
        return token;
    }

    private boolean recentlySent(User user, TokenPurpose purpose) {
        boolean recent = userTokens.existsByUserIdAndPurposeAndCreatedAtAfter(user.getId(), purpose,
                clock.instant().minus(LINK_REQUEST_COOLDOWN));
        if (recent) {
            log.info("Link {} già inviato di recente all'account {}: nessuna nuova email", purpose, user.getId());
        }
        return recent;
    }

    private static boolean isBlocked(User user) {
        return user.getStatus() == AccountStatus.SUSPENDED || user.getStatus() == AccountStatus.REJECTED;
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

    private record IssuedLink(Long tokenId, String url) {
    }

    public record LoginResult(AccessToken accessToken, IssuedRefreshToken refreshToken, User user) {
    }
}

package it.squadra.gestionale.services;

import it.squadra.gestionale.config.AppProperties;
import it.squadra.gestionale.config.AppProperties.BootstrapAdmin;
import it.squadra.gestionale.dto.AuthDto.RegisterRequest;
import it.squadra.gestionale.entities.AuditLog.AuditAction;
import it.squadra.gestionale.entities.User;
import it.squadra.gestionale.entities.User.AccountStatus;
import it.squadra.gestionale.entities.User.Role;
import it.squadra.gestionale.exceptions.ApiException;
import it.squadra.gestionale.repositories.UserRepository;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.time.Clock;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lettura degli utenti, gestione amministrativa di stato e ruolo, creazione del primo Admin.
 * Regole di protezione:
 * <ul>
 *   <li>un Admin non può cambiare il proprio stato (eviterebbe di chiudersi fuori da solo);</li>
 *   <li>l'ultimo Admin attivo non può essere declassato, sospeso o rifiutato;</li>
 *   <li>sospensione e rifiuto chiudono subito tutte le sessioni dell'utente.</li>
 * </ul>
 */
@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private final UserRepository users;
    private final RefreshTokenService refreshTokenService;
    private final AuditService auditService;
    private final PasswordEncoder passwordEncoder;
    private final Validator validator;
    private final AppProperties properties;
    private final Clock clock;

    public UserService(UserRepository users, RefreshTokenService refreshTokenService, AuditService auditService,
                       PasswordEncoder passwordEncoder, Validator validator, AppProperties properties, Clock clock) {
        this.users = users;
        this.refreshTokenService = refreshTokenService;
        this.auditService = auditService;
        this.passwordEncoder = passwordEncoder;
        this.validator = validator;
        this.properties = properties;
        this.clock = clock;
    }

    /** Chiamato all'avvio (vedi AppConfig): crea il primo Admin dalle variabili ADMIN_BOOTSTRAP_*, se impostate. */
    @Transactional
    public Optional<User> createFirstAdminIfConfigured() {
        return createFirstAdmin(properties.bootstrapAdmin());
    }

    /**
     * Crea il primo Admin senza password predefinite nel codice.
     * <ul>
     *   <li>Agisce solo se email e password sono impostate.</li>
     *   <li>Agisce solo se nel database non esiste ancora alcun utente con ruolo ADMIN (in qualsiasi stato).</li>
     *   <li>Dati non validi (es. password debole) bloccano l'avvio: l'errore di configurazione è subito visibile.</li>
     * </ul>
     */
    @Transactional
    public Optional<User> createFirstAdmin(BootstrapAdmin config) {
        if (!config.isConfigured()) {
            return Optional.empty();
        }
        if (users.existsByRole(Role.ADMIN)) {
            log.warn("ADMIN_BOOTSTRAP_* impostate ma esiste già un Admin: nessuna azione. "
                    + "Rimuovere le variabili ADMIN_BOOTSTRAP_* dall'ambiente.");
            return Optional.empty();
        }

        // Stesse regole della registrazione pubblica per nomi, email e password.
        RegisterRequest data = new RegisterRequest(
                defaultIfBlank(config.firstName(), "Admin"),
                defaultIfBlank(config.lastName(), "Squadra"),
                config.email(),
                config.password());
        Set<ConstraintViolation<RegisterRequest>> violations = validator.validate(data);
        if (!violations.isEmpty()) {
            String errors = violations.stream()
                    .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                    .sorted()
                    .collect(Collectors.joining("; "));
            throw new IllegalStateException("Dati ADMIN_BOOTSTRAP_* non validi: " + errors);
        }

        String email = User.normalizeEmail(data.email());
        if (users.existsByEmail(email)) {
            throw new IllegalStateException("ADMIN_BOOTSTRAP_EMAIL appartiene già a un utente esistente: "
                    + "promuoverlo richiede un Admin. Usare un'altra email o intervenire sul database.");
        }

        User admin = new User(data.firstName().trim(), data.lastName().trim(), email,
                passwordEncoder.encode(data.password()));
        admin.setRole(Role.ADMIN);
        admin.changeStatus(AccountStatus.ACTIVE, null, clock.instant());
        admin.setEmailVerifiedAt(clock.instant());
        admin = users.save(admin);

        auditService.record(null, AuditAction.ADMIN_BOOTSTRAPPED, "USER", admin.getId(),
                "Primo amministratore creato da variabili d'ambiente", Map.of("email", email), null);
        log.warn("Primo Admin creato ({}). Rimuovere ora ADMIN_BOOTSTRAP_EMAIL e ADMIN_BOOTSTRAP_PASSWORD "
                + "dall'ambiente e cambiare la password dopo il primo accesso.", email);
        return Optional.of(admin);
    }

    @Transactional(readOnly = true)
    public User getById(Long id) {
        return users.findById(id).orElseThrow(() -> ApiException.notFound("Utente non trovato."));
    }

    @Transactional(readOnly = true)
    public Page<User> list(AccountStatus status, Pageable pageable) {
        return status == null ? users.findAll(pageable) : users.findByStatus(status, pageable);
    }

    @Transactional
    public User approve(Long targetId, Long actorId, String ip) {
        return changeStatus(targetId, actorId, ip, AccountStatus.ACTIVE, Set.of(AccountStatus.PENDING),
                AuditAction.USER_APPROVED, "Utente approvato");
    }

    @Transactional
    public User reject(Long targetId, Long actorId, String ip) {
        return changeStatus(targetId, actorId, ip, AccountStatus.REJECTED, Set.of(AccountStatus.PENDING),
                AuditAction.USER_REJECTED, "Registrazione rifiutata");
    }

    @Transactional
    public User suspend(Long targetId, Long actorId, String ip) {
        return changeStatus(targetId, actorId, ip, AccountStatus.SUSPENDED, Set.of(AccountStatus.ACTIVE),
                AuditAction.USER_SUSPENDED, "Utente sospeso");
    }

    @Transactional
    public User reactivate(Long targetId, Long actorId, String ip) {
        return changeStatus(targetId, actorId, ip, AccountStatus.ACTIVE,
                Set.of(AccountStatus.SUSPENDED, AccountStatus.REJECTED),
                AuditAction.USER_REACTIVATED, "Utente riattivato");
    }

    @Transactional
    public User changeRole(Long targetId, Role newRole, Long actorId, String ip) {
        User actor = getById(actorId);
        User target = getById(targetId);
        Role oldRole = target.getRole();
        if (oldRole == newRole) {
            return target;
        }
        if (oldRole == Role.ADMIN && target.isActive()) {
            ensureNotLastActiveAdmin(target);
        }
        target.setRole(newRole);
        auditService.record(actor, AuditAction.USER_ROLE_CHANGED, "USER", target.getId(),
                "Ruolo modificato da " + oldRole + " a " + newRole,
                Map.of("before", oldRole.name(), "after", newRole.name()), ip);
        return target;
    }

    private User changeStatus(Long targetId, Long actorId, String ip, AccountStatus newStatus,
                              Set<AccountStatus> allowedFrom, AuditAction action, String summary) {
        if (targetId.equals(actorId)) {
            throw ApiException.businessRule("Non puoi modificare lo stato del tuo account.");
        }
        User actor = getById(actorId);
        User target = getById(targetId);
        AccountStatus oldStatus = target.getStatus();
        if (!allowedFrom.contains(oldStatus)) {
            throw ApiException.businessRule("Operazione non consentita per un utente in stato " + oldStatus + ".");
        }
        if (target.getRole() == Role.ADMIN && oldStatus == AccountStatus.ACTIVE && newStatus != AccountStatus.ACTIVE) {
            ensureNotLastActiveAdmin(target);
        }

        target.changeStatus(newStatus, actor, clock.instant());
        if (newStatus != AccountStatus.ACTIVE) {
            refreshTokenService.revokeAllForUser(target.getId(), "ACCOUNT_" + newStatus.name());
        }
        auditService.record(actor, action, "USER", target.getId(), summary,
                Map.of("before", oldStatus.name(), "after", newStatus.name()), ip);
        return target;
    }

    private void ensureNotLastActiveAdmin(User target) {
        boolean otherActiveAdmin = users.lockActiveAdmins().stream()
                .anyMatch(admin -> !admin.getId().equals(target.getId()));
        if (!otherActiveAdmin) {
            throw ApiException.businessRule("Operazione non consentita: è l'ultimo amministratore attivo.");
        }
    }

    private static String defaultIfBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}

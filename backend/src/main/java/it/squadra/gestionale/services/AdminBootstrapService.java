package it.squadra.gestionale.services;

import it.squadra.gestionale.config.BootstrapAdminProperties;
import it.squadra.gestionale.dto.RegisterRequest;
import it.squadra.gestionale.entities.User;
import it.squadra.gestionale.enums.AccountStatus;
import it.squadra.gestionale.enums.AuditAction;
import it.squadra.gestionale.enums.Role;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creazione del primo Admin senza password predefinite nel codice.
 * <ul>
 *   <li>Agisce solo se ADMIN_BOOTSTRAP_EMAIL e ADMIN_BOOTSTRAP_PASSWORD sono impostate.</li>
 *   <li>Agisce solo se nel database non esiste ancora alcun utente con ruolo ADMIN (in qualsiasi stato).</li>
 *   <li>Dati non validi (es. password debole) bloccano l'avvio: l'errore di configurazione è subito visibile.</li>
 * </ul>
 */
@Service
public class AdminBootstrapService {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrapService.class);

    private final BootstrapAdminProperties properties;
    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final Validator validator;
    private final AuditService auditService;
    private final Clock clock;

    public AdminBootstrapService(BootstrapAdminProperties properties, UserRepository users,
                                 PasswordEncoder passwordEncoder, Validator validator, AuditService auditService,
                                 Clock clock) {
        this.properties = properties;
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.validator = validator;
        this.auditService = auditService;
        this.clock = clock;
    }

    @Transactional
    public Optional<User> createFirstAdminIfConfigured() {
        if (!properties.isConfigured()) {
            return Optional.empty();
        }
        if (users.existsByRole(Role.ADMIN)) {
            log.warn("ADMIN_BOOTSTRAP_* impostate ma esiste già un Admin: nessuna azione. "
                    + "Rimuovere le variabili ADMIN_BOOTSTRAP_* dall'ambiente.");
            return Optional.empty();
        }

        RegisterRequest data = new RegisterRequest(
                defaultIfBlank(properties.firstName(), "Admin"),
                defaultIfBlank(properties.lastName(), "Squadra"),
                properties.email(),
                properties.password());
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

    private static String defaultIfBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}

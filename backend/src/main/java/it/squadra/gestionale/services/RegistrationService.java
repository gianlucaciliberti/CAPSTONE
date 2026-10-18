package it.squadra.gestionale.services;

import it.squadra.gestionale.dto.RegisterRequest;
import it.squadra.gestionale.entities.User;
import it.squadra.gestionale.enums.AuditAction;
import it.squadra.gestionale.repositories.UserRepository;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Registrazione pubblica. Il nuovo account nasce sempre PLAYER/PENDING e deve essere approvato da un Admin.
 * Se l'email è già registrata non si segnala nulla al chiamante (nessuna enumerazione degli utenti).
 */
@Service
public class RegistrationService {

    private static final Logger log = LoggerFactory.getLogger(RegistrationService.class);

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    public RegistrationService(UserRepository users, PasswordEncoder passwordEncoder, AuditService auditService) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
    }

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
}

package it.squadra.gestionale.services;

import it.squadra.gestionale.entities.User;
import it.squadra.gestionale.enums.AccountStatus;
import it.squadra.gestionale.enums.AuditAction;
import it.squadra.gestionale.enums.Role;
import it.squadra.gestionale.exceptions.ApiException;
import it.squadra.gestionale.repositories.UserRepository;
import java.time.Clock;
import java.util.Map;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lettura degli utenti e gestione amministrativa di stato e ruolo.
 * Regole di protezione:
 * <ul>
 *   <li>un Admin non può cambiare il proprio stato (eviterebbe di chiudersi fuori da solo);</li>
 *   <li>l'ultimo Admin attivo non può essere declassato, sospeso o rifiutato;</li>
 *   <li>sospensione e rifiuto chiudono subito tutte le sessioni dell'utente.</li>
 * </ul>
 */
@Service
public class UserService {

    private final UserRepository users;
    private final RefreshTokenService refreshTokenService;
    private final AuditService auditService;
    private final Clock clock;

    public UserService(UserRepository users, RefreshTokenService refreshTokenService, AuditService auditService,
                       Clock clock) {
        this.users = users;
        this.refreshTokenService = refreshTokenService;
        this.auditService = auditService;
        this.clock = clock;
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
}

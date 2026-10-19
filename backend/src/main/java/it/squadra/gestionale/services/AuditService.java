package it.squadra.gestionale.services;

import it.squadra.gestionale.entities.AuditLog;
import it.squadra.gestionale.entities.AuditLog.AuditAction;
import it.squadra.gestionale.entities.User;
import it.squadra.gestionale.repositories.AuditLogRepository;
import java.time.Clock;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * Scrittura dell'audit log. Va chiamato dentro la transazione dell'operazione registrata:
 * se l'operazione fallisce, anche la voce di audit viene annullata (e viceversa).
 * I dettagli non devono mai contenere password, token o altri segreti.
 */
@Service
public class AuditService {

    private final AuditLogRepository repository;
    private final JsonMapper jsonMapper;
    private final Clock clock;

    public AuditService(AuditLogRepository repository, JsonMapper jsonMapper, Clock clock) {
        this.repository = repository;
        this.jsonMapper = jsonMapper;
        this.clock = clock;
    }

    @Transactional
    public void record(User actor, AuditAction action, String entityType, Object entityId, String summary,
                       Map<String, ?> details, String ipAddress) {
        String json = details == null || details.isEmpty() ? null : jsonMapper.writeValueAsString(details);
        repository.save(new AuditLog(clock.instant(), actor, action.name(), entityType,
                entityId == null ? null : String.valueOf(entityId), summary, json, ipAddress));
    }
}

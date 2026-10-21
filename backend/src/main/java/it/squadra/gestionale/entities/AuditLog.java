package it.squadra.gestionale.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Voce dell'audit log. Append-only: l'entità è immutabile per Hibernate e un trigger PostgreSQL
 * rifiuta UPDATE, DELETE e TRUNCATE sulla tabella.
 */
@Entity
@Immutable
@Table(name = "audit_logs")
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    /** Null per operazioni di sistema (es. worker email). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_id")
    private User actor;

    /** Copia dell'email dell'autore, per leggere lo storico anche se l'utente cambia email. */
    @Column(name = "actor_email", length = 254)
    private String actorEmail;

    @Column(nullable = false, length = 60)
    private String action;

    @Column(name = "entity_type", nullable = false, length = 40)
    private String entityType;

    @Column(name = "entity_id", length = 64)
    private String entityId;

    @Column(length = 500)
    private String summary;

    /** Dettagli strutturati (es. valori prima/dopo) in JSON. Mai password, token o segreti. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String details;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    protected AuditLog() {
    }

    public AuditLog(Instant occurredAt, User actor, String action, String entityType, String entityId,
                    String summary, String details, String ipAddress) {
        this.occurredAt = occurredAt;
        this.actor = actor;
        this.actorEmail = actor == null ? null : actor.getEmail();
        this.action = action;
        this.entityType = entityType;
        this.entityId = entityId;
        this.summary = summary;
        this.details = details;
        this.ipAddress = ipAddress;
    }

    public Long getId() {
        return id;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public User getActor() {
        return actor;
    }

    public String getActorEmail() {
        return actorEmail;
    }

    public String getAction() {
        return action;
    }

    public String getEntityType() {
        return entityType;
    }

    public String getEntityId() {
        return entityId;
    }

    public String getSummary() {
        return summary;
    }

    public String getDetails() {
        return details;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    /** Azioni registrate nell'audit log. Il nome viene salvato nella colonna audit_logs.action. */
    public enum AuditAction {
        ADMIN_BOOTSTRAPPED,
        USER_REGISTERED,
        USER_APPROVED,
        USER_REJECTED,
        USER_SUSPENDED,
        USER_REACTIVATED,
        USER_ROLE_CHANGED,
        EMAIL_VERIFIED,
        PASSWORD_CHANGED,
        PASSWORD_RESET_REQUESTED,
        PASSWORD_RESET,
        MATCH_CREATED,
        MATCH_UPDATED,
        MATCH_CANCELLED,
        AVAILABILITY_OVERRIDDEN,
        CALLUP_PUBLISHED,
        CALLUP_UPDATED,
        CALLUP_WITHDRAWN,
        EMAIL_RETRIED,
        EMAIL_CANCELLED,
        MATCH_RESULT_RECORDED,
        MATCH_EVENT_CREATED,
        MATCH_EVENT_UPDATED,
        MATCH_EVENT_DELETED,
        PAYMENT_REQUEST_CREATED,
        PAYMENT_REQUEST_UPDATED,
        PAYMENT_REQUEST_CANCELLED,
        PAYMENT_ASSIGNED,
        PAYMENT_CONFIRMED,
        PAYMENT_WAIVED,
        PAYMENT_REOPENED
    }
}

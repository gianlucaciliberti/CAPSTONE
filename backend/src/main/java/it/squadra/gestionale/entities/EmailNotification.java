package it.squadra.gestionale.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Duration;
import java.time.Instant;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

/**
 * Email da inviare/inviata. Funziona da transactional outbox: viene salvata nella stessa transazione
 * dell'operazione che la genera e inviata in seguito da un worker con retry limitati.
 */
@Entity
@Table(name = "email_notifications")
public class EmailNotification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Chiave univoca che impedisce di accodare due volte la stessa email (es. "CALLUP_PUBLISHED:12:r3:user:7"). */
    @Column(name = "idempotency_key", nullable = false, length = 150, unique = true, updatable = false)
    private String idempotencyKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "notification_type", nullable = false, length = 40, updatable = false)
    private NotificationType type;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recipient_user_id", updatable = false)
    private User recipientUser;

    @Column(name = "recipient_email", nullable = false, length = 254, updatable = false)
    private String recipientEmail;

    @Column(name = "recipient_name", length = 170, updatable = false)
    private String recipientName;

    @Column(nullable = false, length = 200)
    private String subject;

    /** Variabili del template in JSON. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String payload;

    @Column(name = "related_entity_type", length = 40, updatable = false)
    private String relatedEntityType;

    @Column(name = "related_entity_id", length = 64, updatable = false)
    private String relatedEntityId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EmailStatus status = EmailStatus.PENDING;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "max_attempts", nullable = false)
    private int maxAttempts;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(name = "last_error", length = 1000)
    private String lastError;

    @Column(name = "provider_message_id", length = 100)
    private String providerMessageId;

    @Column(name = "sent_at")
    private Instant sentAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    protected EmailNotification() {
    }

    public EmailNotification(String idempotencyKey, NotificationType type, User recipientUser,
                             String recipientEmail, String recipientName, String subject, String payload,
                             String relatedEntityType, String relatedEntityId, int maxAttempts,
                             Instant nextAttemptAt) {
        this.idempotencyKey = idempotencyKey;
        this.type = type;
        this.recipientUser = recipientUser;
        this.recipientEmail = recipientEmail;
        this.recipientName = recipientName;
        this.subject = subject;
        this.payload = payload;
        this.relatedEntityType = relatedEntityType;
        this.relatedEntityId = relatedEntityId;
        this.maxAttempts = maxAttempts;
        this.nextAttemptAt = nextAttemptAt;
    }

    public Long getId() {
        return id;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public NotificationType getType() {
        return type;
    }

    public User getRecipientUser() {
        return recipientUser;
    }

    public String getRecipientEmail() {
        return recipientEmail;
    }

    public String getRecipientName() {
        return recipientName;
    }

    public String getSubject() {
        return subject;
    }

    public String getPayload() {
        return payload;
    }

    public void setPayload(String payload) {
        this.payload = payload;
    }

    public String getRelatedEntityType() {
        return relatedEntityType;
    }

    public String getRelatedEntityId() {
        return relatedEntityId;
    }

    public EmailStatus getStatus() {
        return status;
    }

    public void setStatus(EmailStatus status) {
        this.status = status;
    }

    public int getAttempts() {
        return attempts;
    }

    public void setAttempts(int attempts) {
        this.attempts = attempts;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public void setMaxAttempts(int maxAttempts) {
        this.maxAttempts = maxAttempts;
    }

    public Instant getNextAttemptAt() {
        return nextAttemptAt;
    }

    public void setNextAttemptAt(Instant nextAttemptAt) {
        this.nextAttemptAt = nextAttemptAt;
    }

    public Instant getLockedUntil() {
        return lockedUntil;
    }

    public void setLockedUntil(Instant lockedUntil) {
        this.lockedUntil = lockedUntil;
    }

    public String getLastError() {
        return lastError;
    }

    public void setLastError(String lastError) {
        this.lastError = lastError;
    }

    public String getProviderMessageId() {
        return providerMessageId;
    }

    public void setProviderMessageId(String providerMessageId) {
        this.providerMessageId = providerMessageId;
    }

    public Instant getSentAt() {
        return sentAt;
    }

    public void setSentAt(Instant sentAt) {
        this.sentAt = sentAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public long getVersion() {
        return version;
    }

    /** Invio riuscito. Per i tipi sensibili il contenuto (con link e token monouso) viene cancellato. */
    public void markSent(String providerMessageId, Instant now) {
        this.status = EmailStatus.SENT;
        this.attempts++;
        this.providerMessageId = providerMessageId;
        this.sentAt = now;
        this.lastError = null;
        if (type.isSensitive()) {
            this.payload = "{\"scrubbed\":true}";
        }
    }

    /** Tentativo fallito: nuovo tentativo dopo {@code retryDelay}, oppure FAILED se i tentativi sono esauriti. */
    public void markAttemptFailed(String error, Instant now, Duration retryDelay) {
        this.attempts++;
        this.lastError = error == null ? null : error.substring(0, Math.min(error.length(), 1000));
        if (attempts >= maxAttempts) {
            this.status = EmailStatus.FAILED;
        } else {
            this.nextAttemptAt = now.plus(retryDelay);
        }
    }

    public enum NotificationType {
        CALLUP_PUBLISHED,
        CALLUP_UPDATED,
        CALLUP_WITHDRAWN,
        CALLUP_REMOVED,
        ACCOUNT_APPROVED,
        EMAIL_VERIFICATION,
        PASSWORD_RESET,
        PAYMENT_REQUEST;

        /** Email con link contenenti token monouso: il contenuto non va conservato dopo l'invio. */
        public boolean isSensitive() {
            return this == EMAIL_VERIFICATION || this == PASSWORD_RESET;
        }
    }

    public enum EmailStatus {
        /** In coda, in attesa di invio o di un nuovo tentativo. */
        PENDING,
        /** Presa in carico da un worker (locked_until protegge da invii concorrenti). */
        SENDING,
        SENT,
        /** Tentativi esauriti: richiede un retry manuale dell'Admin. */
        FAILED,
        CANCELLED
    }
}

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
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.time.Instant;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "availabilities",
        uniqueConstraints = @UniqueConstraint(name = "uk_availabilities_match_user", columnNames = {"match_id", "user_id"}))
public class Availability {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "match_id", nullable = false, updatable = false)
    private Match match;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AvailabilityStatus status = AvailabilityStatus.PENDING;

    @Column(length = 500)
    private String note;

    /** True se l'ultima modifica è una correzione amministrativa (es. dopo la pubblicazione delle convocazioni). */
    @Column(name = "admin_override", nullable = false)
    private boolean adminOverride;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "updated_by", nullable = false)
    private User updatedBy;

    @Version
    private long version;

    protected Availability() {
    }

    public Availability(Match match, User user) {
        this.match = match;
        this.user = user;
        this.updatedBy = user;
    }

    public void update(AvailabilityStatus status, String note, User by, boolean adminOverride) {
        this.status = status;
        this.note = note;
        this.updatedBy = by;
        this.adminOverride = adminOverride;
    }

    public Long getId() {
        return id;
    }

    public Match getMatch() {
        return match;
    }

    public User getUser() {
        return user;
    }

    public AvailabilityStatus getStatus() {
        return status;
    }

    public String getNote() {
        return note;
    }

    public boolean isAdminOverride() {
        return adminOverride;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public User getUpdatedBy() {
        return updatedBy;
    }

    public long getVersion() {
        return version;
    }

    public enum AvailabilityStatus {
        /** Disponibile. */
        AVAILABLE,
        /** Non disponibile. */
        UNAVAILABLE,
        /** In attesa di risposta (equivale all'assenza di una risposta). */
        PENDING
    }
}

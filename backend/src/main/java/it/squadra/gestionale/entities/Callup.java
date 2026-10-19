package it.squadra.gestionale.entities;

import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/** Convocazione di una partita (al massimo una per partita). */
@Entity
@Table(name = "callups")
public class Callup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "match_id", nullable = false, updatable = false, unique = true)
    private Match match;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CallupStatus status = CallupStatus.DRAFT;

    /** Orario di ritrovo facoltativo. */
    @Column(name = "meeting_at")
    private Instant meetingAt;

    @Column(length = 2000)
    private String notes;

    /** Numero dell'ultima revisione registrata (0 = mai pubblicata). */
    @Column(name = "current_revision", nullable = false)
    private int currentRevision;

    @Column(name = "published_at")
    private Instant publishedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "published_by")
    private User publishedBy;

    @Column(name = "withdrawn_at")
    private Instant withdrawnAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "withdrawn_by")
    private User withdrawnBy;

    @Column(name = "withdrawal_reason", length = 500)
    private String withdrawalReason;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false, updatable = false)
    private User createdBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "updated_by")
    private User updatedBy;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    @OneToMany(mappedBy = "callup", cascade = CascadeType.PERSIST)
    private List<CallupPlayer> players = new ArrayList<>();

    protected Callup() {
    }

    public Callup(Match match, User createdBy) {
        this.match = match;
        this.createdBy = createdBy;
    }

    public List<CallupPlayer> getActivePlayers() {
        return players.stream().filter(CallupPlayer::isActive).toList();
    }

    public Long getId() {
        return id;
    }

    public Match getMatch() {
        return match;
    }

    public CallupStatus getStatus() {
        return status;
    }

    public void setStatus(CallupStatus status) {
        this.status = status;
    }

    public Instant getMeetingAt() {
        return meetingAt;
    }

    public void setMeetingAt(Instant meetingAt) {
        this.meetingAt = meetingAt;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public int getCurrentRevision() {
        return currentRevision;
    }

    public void setCurrentRevision(int currentRevision) {
        this.currentRevision = currentRevision;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public User getPublishedBy() {
        return publishedBy;
    }

    public void markPublished(User by, Instant when) {
        this.status = CallupStatus.PUBLISHED;
        this.publishedBy = by;
        this.publishedAt = when;
        this.updatedBy = by;
    }

    public Instant getWithdrawnAt() {
        return withdrawnAt;
    }

    public User getWithdrawnBy() {
        return withdrawnBy;
    }

    public String getWithdrawalReason() {
        return withdrawalReason;
    }

    public void markWithdrawn(String reason, User by, Instant when) {
        this.status = CallupStatus.WITHDRAWN;
        this.withdrawalReason = reason;
        this.withdrawnBy = by;
        this.withdrawnAt = when;
        this.updatedBy = by;
    }

    public User getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public User getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(User updatedBy) {
        this.updatedBy = updatedBy;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public long getVersion() {
        return version;
    }

    public List<CallupPlayer> getPlayers() {
        return players;
    }

    public enum CallupStatus {
        /** Bozza, visibile solo ad Allenatori e Admin. */
        DRAFT,
        /** Pubblicata: visibile ai convocati, disponibilità bloccate. */
        PUBLISHED,
        /** Ritirata dopo la pubblicazione; lo storico resta consultabile. */
        WITHDRAWN
    }
}

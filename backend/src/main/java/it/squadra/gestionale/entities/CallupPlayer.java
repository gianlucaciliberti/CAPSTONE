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

/**
 * Convocato. Le rimozioni sono logiche (removed_at) per conservare lo storico e capire,
 * alla pubblicazione successiva, chi va avvisato.
 */
@Entity
@Table(name = "callup_players")
public class CallupPlayer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "callup_id", nullable = false, updatable = false)
    private Callup callup;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    @Column(name = "added_at", nullable = false, updatable = false)
    private Instant addedAt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "added_by", nullable = false, updatable = false)
    private User addedBy;

    @Column(name = "added_in_revision")
    private Integer addedInRevision;

    @Column(name = "removed_at")
    private Instant removedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "removed_by")
    private User removedBy;

    @Column(name = "removed_in_revision")
    private Integer removedInRevision;

    protected CallupPlayer() {
    }

    public CallupPlayer(Callup callup, User user, User addedBy, Instant addedAt) {
        this.callup = callup;
        this.user = user;
        this.addedBy = addedBy;
        this.addedAt = addedAt;
    }

    public boolean isActive() {
        return removedAt == null;
    }

    public void remove(User by, Instant when) {
        this.removedBy = by;
        this.removedAt = when;
    }

    public Long getId() {
        return id;
    }

    public Callup getCallup() {
        return callup;
    }

    public User getUser() {
        return user;
    }

    public Instant getAddedAt() {
        return addedAt;
    }

    public User getAddedBy() {
        return addedBy;
    }

    public Integer getAddedInRevision() {
        return addedInRevision;
    }

    public void setAddedInRevision(Integer addedInRevision) {
        this.addedInRevision = addedInRevision;
    }

    public Instant getRemovedAt() {
        return removedAt;
    }

    public User getRemovedBy() {
        return removedBy;
    }

    public Integer getRemovedInRevision() {
        return removedInRevision;
    }

    public void setRemovedInRevision(Integer removedInRevision) {
        this.removedInRevision = removedInRevision;
    }
}

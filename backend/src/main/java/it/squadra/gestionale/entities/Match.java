package it.squadra.gestionale.entities;

import it.squadra.gestionale.enums.MatchStatus;
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
import java.time.Instant;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "matches")
public class Match {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String opponent;

    /** Istante del calcio d'inizio (UTC nel database, mostrato in Europe/Rome). */
    @Column(name = "kickoff_at", nullable = false)
    private Instant kickoffAt;

    @Column(name = "venue_name", nullable = false, length = 150)
    private String venueName;

    @Column(name = "venue_address", nullable = false, length = 255)
    private String venueAddress;

    @Column(length = 2000)
    private String notes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MatchStatus status = MatchStatus.SCHEDULED;

    @Column(name = "team_goals")
    private Integer teamGoals;

    @Column(name = "opponent_goals")
    private Integer opponentGoals;

    @Column(name = "result_updated_at")
    private Instant resultUpdatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "result_updated_by")
    private User resultUpdatedBy;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cancelled_by")
    private User cancelledBy;

    @Column(name = "cancellation_reason", length = 500)
    private String cancellationReason;

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

    protected Match() {
    }

    public Match(String opponent, Instant kickoffAt, String venueName, String venueAddress, String notes,
                 User createdBy) {
        this.opponent = opponent;
        this.kickoffAt = kickoffAt;
        this.venueName = venueName;
        this.venueAddress = venueAddress;
        this.notes = notes;
        this.createdBy = createdBy;
    }

    public void updateDetails(String opponent, Instant kickoffAt, String venueName, String venueAddress,
                              String notes, User updatedBy) {
        this.opponent = opponent;
        this.kickoffAt = kickoffAt;
        this.venueName = venueName;
        this.venueAddress = venueAddress;
        this.notes = notes;
        this.updatedBy = updatedBy;
    }

    public void cancel(String reason, User by, Instant when) {
        this.status = MatchStatus.CANCELLED;
        this.cancellationReason = reason;
        this.cancelledBy = by;
        this.cancelledAt = when;
        this.updatedBy = by;
    }

    public void recordResult(int teamGoals, int opponentGoals, User by, Instant when) {
        this.teamGoals = teamGoals;
        this.opponentGoals = opponentGoals;
        this.status = MatchStatus.COMPLETED;
        this.resultUpdatedBy = by;
        this.resultUpdatedAt = when;
        this.updatedBy = by;
    }

    public Long getId() {
        return id;
    }

    public String getOpponent() {
        return opponent;
    }

    public Instant getKickoffAt() {
        return kickoffAt;
    }

    public String getVenueName() {
        return venueName;
    }

    public String getVenueAddress() {
        return venueAddress;
    }

    public String getNotes() {
        return notes;
    }

    public MatchStatus getStatus() {
        return status;
    }

    public Integer getTeamGoals() {
        return teamGoals;
    }

    public Integer getOpponentGoals() {
        return opponentGoals;
    }

    public Instant getResultUpdatedAt() {
        return resultUpdatedAt;
    }

    public User getResultUpdatedBy() {
        return resultUpdatedBy;
    }

    public Instant getCancelledAt() {
        return cancelledAt;
    }

    public User getCancelledBy() {
        return cancelledBy;
    }

    public String getCancellationReason() {
        return cancellationReason;
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

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public long getVersion() {
        return version;
    }
}

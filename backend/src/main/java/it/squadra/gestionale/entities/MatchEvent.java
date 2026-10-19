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
import java.time.Instant;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/** Evento del tabellino: gol, autogol, ammonizione o espulsione. */
@Entity
@Table(name = "match_events")
public class MatchEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "match_id", nullable = false, updatable = false)
    private Match match;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 20)
    private MatchEventType eventType;

    @Enumerated(EnumType.STRING)
    @Column(name = "team_side", nullable = false, length = 10)
    private TeamSide teamSide;

    /** Giocatore della squadra con account; null per avversari o giocatori senza account. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "player_id")
    private User player;

    /** Nome libero per avversari o giocatori senza account. */
    @Column(name = "player_name", length = 120)
    private String playerName;

    @Column(name = "event_minute")
    private Integer minute;

    @Column(length = 500)
    private String notes;

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

    protected MatchEvent() {
    }

    public MatchEvent(Match match, MatchEventType eventType, TeamSide teamSide, User player, String playerName,
                      Integer minute, String notes, User createdBy) {
        this.match = match;
        this.createdBy = createdBy;
        update(eventType, teamSide, player, playerName, minute, notes, null);
    }

    public void update(MatchEventType eventType, TeamSide teamSide, User player, String playerName,
                       Integer minute, String notes, User updatedBy) {
        this.eventType = eventType;
        this.teamSide = teamSide;
        this.player = player;
        this.playerName = playerName;
        this.minute = minute;
        this.notes = notes;
        this.updatedBy = updatedBy;
    }

    /** True se l'evento assegna un gol alla nostra squadra (gol nostro o autogol avversario). */
    public boolean countsForTeam() {
        return (eventType == MatchEventType.GOAL && teamSide == TeamSide.TEAM)
                || (eventType == MatchEventType.OWN_GOAL && teamSide == TeamSide.OPPONENT);
    }

    /** True se l'evento assegna un gol all'avversario (gol avversario o nostro autogol). */
    public boolean countsForOpponent() {
        return (eventType == MatchEventType.GOAL && teamSide == TeamSide.OPPONENT)
                || (eventType == MatchEventType.OWN_GOAL && teamSide == TeamSide.TEAM);
    }

    public Long getId() {
        return id;
    }

    public Match getMatch() {
        return match;
    }

    public MatchEventType getEventType() {
        return eventType;
    }

    public TeamSide getTeamSide() {
        return teamSide;
    }

    public User getPlayer() {
        return player;
    }

    public String getPlayerName() {
        return playerName;
    }

    public Integer getMinute() {
        return minute;
    }

    public String getNotes() {
        return notes;
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

    public enum MatchEventType {
        GOAL,
        OWN_GOAL,
        YELLOW_CARD,
        RED_CARD
    }

    /** Squadra del giocatore che compie l'evento (non necessariamente la squadra che beneficia del gol). */
    public enum TeamSide {
        TEAM,
        OPPONENT
    }
}

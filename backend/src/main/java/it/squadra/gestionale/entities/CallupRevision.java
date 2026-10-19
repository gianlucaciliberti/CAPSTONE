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
import java.time.Instant;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Revisione immutabile di una convocazione: snapshot JSON di dettagli e convocati al momento dell'azione. */
@Entity
@Immutable
@Table(name = "callup_revisions")
public class CallupRevision {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "callup_id", nullable = false)
    private Callup callup;

    @Column(name = "revision_number", nullable = false)
    private int revisionNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CallupRevisionAction action;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String snapshot;

    @Column(name = "change_summary", length = 1000)
    private String changeSummary;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "author_id", nullable = false)
    private User author;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected CallupRevision() {
    }

    public CallupRevision(Callup callup, int revisionNumber, CallupRevisionAction action, String snapshot,
                          String changeSummary, User author, Instant createdAt) {
        this.callup = callup;
        this.revisionNumber = revisionNumber;
        this.action = action;
        this.snapshot = snapshot;
        this.changeSummary = changeSummary;
        this.author = author;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public Callup getCallup() {
        return callup;
    }

    public int getRevisionNumber() {
        return revisionNumber;
    }

    public CallupRevisionAction getAction() {
        return action;
    }

    public String getSnapshot() {
        return snapshot;
    }

    public String getChangeSummary() {
        return changeSummary;
    }

    public User getAuthor() {
        return author;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public enum CallupRevisionAction {
        PUBLISHED,
        UPDATED,
        WITHDRAWN
    }
}

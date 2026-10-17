package it.squadra.gestionale.entities;

import it.squadra.gestionale.enums.PaymentAssignmentStatus;
import it.squadra.gestionale.enums.PaymentMethod;
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
import java.time.LocalDate;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/** Quota di una richiesta economica assegnata a un utente. Solo l'Admin può confermarne il pagamento. */
@Entity
@Table(name = "payment_assignments",
        uniqueConstraints = @UniqueConstraint(name = "uk_payment_assignments_request_user",
                columnNames = {"payment_request_id", "user_id"}))
public class PaymentAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payment_request_id", nullable = false, updatable = false)
    private PaymentRequest paymentRequest;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    @Column(name = "amount_cents", nullable = false)
    private int amountCents;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentAssignmentStatus status = PaymentAssignmentStatus.PENDING;

    /** Data in cui il pagamento è stato effettivamente ricevuto. */
    @Column(name = "paid_on")
    private LocalDate paidOn;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", length = 20)
    private PaymentMethod paymentMethod;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "confirmed_by")
    private User confirmedBy;

    @Column(name = "admin_note", length = 500)
    private String adminNote;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cancelled_by")
    private User cancelledBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    protected PaymentAssignment() {
    }

    PaymentAssignment(PaymentRequest paymentRequest, User user, int amountCents) {
        this.paymentRequest = paymentRequest;
        this.user = user;
        this.amountCents = amountCents;
    }

    public void confirmPayment(LocalDate paidOn, PaymentMethod method, String adminNote, User by, Instant when) {
        this.status = PaymentAssignmentStatus.PAID;
        this.paidOn = paidOn;
        this.paymentMethod = method;
        this.adminNote = adminNote;
        this.confirmedBy = by;
        this.confirmedAt = when;
    }

    public void cancel(User by, Instant when) {
        this.status = PaymentAssignmentStatus.CANCELLED;
        this.cancelledBy = by;
        this.cancelledAt = when;
    }

    public Long getId() {
        return id;
    }

    public PaymentRequest getPaymentRequest() {
        return paymentRequest;
    }

    public User getUser() {
        return user;
    }

    public int getAmountCents() {
        return amountCents;
    }

    public PaymentAssignmentStatus getStatus() {
        return status;
    }

    public LocalDate getPaidOn() {
        return paidOn;
    }

    public PaymentMethod getPaymentMethod() {
        return paymentMethod;
    }

    public Instant getConfirmedAt() {
        return confirmedAt;
    }

    public User getConfirmedBy() {
        return confirmedBy;
    }

    public String getAdminNote() {
        return adminNote;
    }

    public Instant getCancelledAt() {
        return cancelledAt;
    }

    public User getCancelledBy() {
        return cancelledBy;
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
}

package it.squadra.gestionale.dto;

import it.squadra.gestionale.entities.PaymentAssignment.PaymentAssignmentStatus;
import it.squadra.gestionale.entities.PaymentAssignment.PaymentMethod;
import it.squadra.gestionale.entities.PaymentRequest.PaymentRequestStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/** DTO dei pagamenti (/api/v1/payments). Importi sempre in centesimi di euro. */
public final class PaymentDto {

    private PaymentDto() {
    }

    /** Nuova richiesta: la stessa quota viene assegnata a ciascun utente indicato (solo utenti attivi). */
    public record CreateRequest(
            @NotBlank(message = "La descrizione è obbligatoria.")
            @Size(max = 200, message = "La descrizione può avere al massimo 200 caratteri.")
            String description,

            @NotNull(message = "Indica l'importo.")
            @Min(value = 1, message = "L'importo deve essere maggiore di zero.")
            @Max(value = 1_000_000, message = "Importo troppo alto.")
            Integer amountCents,

            LocalDate dueDate,

            @Size(max = 1000, message = "Le note possono avere al massimo 1000 caratteri.")
            String notes,

            @NotEmpty(message = "Indica almeno un destinatario.")
            @Size(max = 200, message = "Troppi destinatari.")
            List<@NotNull Long> userIds) {
    }

    /** Modifica dei dettagli; l'importo non si cambia (è già nelle quote). */
    public record UpdateRequest(
            @NotBlank(message = "La descrizione è obbligatoria.")
            @Size(max = 200, message = "La descrizione può avere al massimo 200 caratteri.")
            String description,

            LocalDate dueDate,

            @Size(max = 1000, message = "Le note possono avere al massimo 1000 caratteri.")
            String notes,

            @NotNull(message = "Versione mancante: ricarica la pagina.")
            Long version) {
    }

    public record CancelRequest(
            @Size(max = 500, message = "Il motivo può avere al massimo 500 caratteri.")
            String reason,

            @NotNull(message = "Versione mancante: ricarica la pagina.")
            Long version) {
    }

    public record AssignRequest(
            @NotEmpty(message = "Indica almeno un destinatario.")
            @Size(max = 200, message = "Troppi destinatari.")
            List<@NotNull Long> userIds) {
    }

    /** Conferma manuale di un pagamento ricevuto. {@code paidOn} non può essere nel futuro. */
    public record ConfirmRequest(
            @NotNull(message = "Indica la data del pagamento.")
            LocalDate paidOn,

            @NotNull(message = "Indica il metodo di pagamento.")
            PaymentMethod method,

            @Size(max = 500, message = "La nota può avere al massimo 500 caratteri.")
            String note,

            @NotNull(message = "Versione mancante: ricarica la pagina.")
            Long version) {
    }

    /** Operazioni su una quota che richiedono solo la versione (esonero, riapertura). */
    public record VersionRequest(
            @NotNull(message = "Versione mancante: ricarica la pagina.")
            Long version) {
    }

    /** Richiesta con i totali calcolati dalle quote (le quote annullate non contano). */
    public record PaymentRequestResponse(
            Long id,
            String description,
            int amountCents,
            String currency,
            LocalDate dueDate,
            String notes,
            PaymentRequestStatus status,
            OffsetDateTime createdAt,
            String cancellationReason,
            int pendingCount,
            int paidCount,
            int cancelledCount,
            int collectedCents,
            int outstandingCents,
            long version) {
    }

    /** Quota vista dall'Admin. */
    public record AssignmentResponse(
            Long id,
            Long userId,
            String userName,
            int amountCents,
            PaymentAssignmentStatus status,
            LocalDate paidOn,
            PaymentMethod paymentMethod,
            String adminNote,
            OffsetDateTime confirmedAt,
            String confirmedBy,
            long version) {
    }

    public record PaymentRequestDetail(PaymentRequestResponse request, List<AssignmentResponse> assignments) {
    }

    /** Quota vista dal destinatario: senza la nota interna dell'Admin. */
    public record MyPayment(
            Long assignmentId,
            Long requestId,
            String description,
            String notes,
            LocalDate dueDate,
            int amountCents,
            PaymentAssignmentStatus status,
            LocalDate paidOn,
            PaymentMethod paymentMethod,
            boolean overdue) {
    }

    /** Promemoria manuale: numero di email accodate (al massimo una al giorno per quota). */
    public record RemindResponse(int queued) {
    }

    /** Pagamenti dell'utente: {@code outstandingCents} è il totale ancora da pagare. */
    public record MyPaymentsResponse(int outstandingCents, int overdueCount, List<MyPayment> payments) {
    }
}

package it.squadra.gestionale.dto;

import it.squadra.gestionale.dto.MatchDto.MatchResponse;
import it.squadra.gestionale.entities.Availability.AvailabilityStatus;
import java.util.List;

/** DTO della dashboard (/api/v1/dashboard): tutto ciò che serve alla home in una sola chiamata. */
public final class DashboardDto {

    private DashboardDto() {
    }

    /**
     * Dashboard dell'utente autenticato. {@code nextMatch} e {@code lastResult} sono null se non esistono;
     * {@code staff} è valorizzato solo per Allenatori e Admin.
     */
    public record DashboardResponse(
            NextMatch nextMatch,
            MatchResponse lastResult,
            PaymentSummary payments,
            StaffTasks staff) {
    }

    /**
     * Prossima partita dal punto di vista dell'utente: la sua disponibilità (PENDING se non ha risposto),
     * se la convocazione è pubblicata e se lui è tra i convocati. {@code availabilityOpen}: la disponibilità
     * si può ancora modificare (partita futura e convocazione non pubblicata).
     */
    public record NextMatch(
            MatchResponse match,
            AvailabilityStatus myAvailability,
            boolean availabilityOpen,
            boolean callupPublished,
            boolean convocated) {
    }

    public record PaymentSummary(int outstandingCents, int overdueCount) {
    }

    /**
     * Cose da fare per lo staff. {@code matchesWithoutCallup}: partite dei prossimi giorni senza convocazione
     * pubblicata; {@code failedEmails}: invii falliti. I campi solo Admin sono null per gli Allenatori:
     * {@code resultsToRecord} (partite giocate senza risultato), {@code pendingApprovals} (registrazioni con email
     * verificata da approvare), {@code outstandingPaymentsCents} (totale da incassare).
     */
    public record StaffTasks(
            List<MatchResponse> matchesWithoutCallup,
            long failedEmails,
            List<MatchResponse> resultsToRecord,
            Long pendingApprovals,
            Long outstandingPaymentsCents) {
    }
}

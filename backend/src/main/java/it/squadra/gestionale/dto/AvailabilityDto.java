package it.squadra.gestionale.dto;

import it.squadra.gestionale.dto.MatchDto.MatchResponse;
import it.squadra.gestionale.entities.Availability.AvailabilityStatus;
import it.squadra.gestionale.entities.User.Role;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/** DTO delle disponibilità (/api/v1/matches/{id}/availability e /api/v1/availability). */
public final class AvailabilityDto {

    private AvailabilityDto() {
    }

    public record AvailabilityRequest(
            @NotNull(message = "Indica la disponibilità.")
            AvailabilityStatus status,

            @Size(max = 500, message = "La nota può avere al massimo 500 caratteri.")
            String note) {
    }

    /**
     * Disponibilità dell'utente per una partita. Senza risposta lo stato è PENDING ("in attesa di risposta").
     * {@code editable} false indica che l'utente non può più modificarla ({@code lockReason} spiega il motivo).
     */
    public record MyAvailabilityResponse(
            Long matchId,
            AvailabilityStatus status,
            String note,
            boolean adminOverride,
            OffsetDateTime updatedAt,
            boolean editable,
            String lockReason) {
    }

    public record PlayerAvailability(
            Long userId,
            String firstName,
            String lastName,
            Role role,
            AvailabilityStatus status,
            String note,
            boolean adminOverride,
            OffsetDateTime updatedAt) {
    }

    /** Riepilogo della squadra per una partita (Allenatori e Admin): tutti gli utenti attivi. */
    public record TeamAvailabilityResponse(
            Long matchId,
            boolean editable,
            String lockReason,
            Map<AvailabilityStatus, Long> counts,
            List<PlayerAvailability> players) {
    }

    /** Prossima partita con la disponibilità dell'utente. */
    public record UpcomingAvailability(MatchResponse match, MyAvailabilityResponse availability) {
    }
}

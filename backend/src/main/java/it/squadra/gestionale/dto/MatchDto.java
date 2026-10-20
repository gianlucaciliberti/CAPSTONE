package it.squadra.gestionale.dto;

import it.squadra.gestionale.entities.Match;
import it.squadra.gestionale.entities.Match.MatchStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;

/** DTO delle partite (/api/v1/matches). */
public final class MatchDto {

    private MatchDto() {
    }

    /**
     * Creazione o modifica di una partita. {@code kickoff} è data e ora locali della squadra (Europe/Rome),
     * es. "2026-11-07T15:30": la conversione in istante UTC avviene sul server, anche a cavallo dell'ora legale.
     * {@code version} è obbligatoria in modifica: se un altro Admin ha salvato nel frattempo si ottiene 409.
     */
    public record MatchRequest(
            @NotBlank(message = "L'avversario è obbligatorio.")
            @Size(max = 120, message = "L'avversario può avere al massimo 120 caratteri.")
            String opponent,

            @NotNull(message = "Data e ora sono obbligatorie.")
            LocalDateTime kickoff,

            @NotBlank(message = "Il luogo è obbligatorio.")
            @Size(max = 150, message = "Il luogo può avere al massimo 150 caratteri.")
            String venueName,

            @NotBlank(message = "L'indirizzo è obbligatorio.")
            @Size(max = 255, message = "L'indirizzo può avere al massimo 255 caratteri.")
            String venueAddress,

            @Size(max = 2000, message = "Le note possono avere al massimo 2000 caratteri.")
            String notes,

            Long version) {
    }

    public record CancelMatchRequest(
            @Size(max = 500, message = "Il motivo può avere al massimo 500 caratteri.")
            String reason,

            @NotNull(message = "Versione mancante: ricarica la pagina.")
            Long version) {
    }

    /** Partita restituita dalle API. Gli orari sono espressi con l'offset di Europe/Rome (es. +01:00 o +02:00). */
    public record MatchResponse(
            Long id,
            String opponent,
            OffsetDateTime kickoffAt,
            String venueName,
            String venueAddress,
            String notes,
            MatchStatus status,
            Integer teamGoals,
            Integer opponentGoals,
            String cancellationReason,
            long version) {

        public static MatchResponse from(Match match, ZoneId zone) {
            return new MatchResponse(match.getId(), match.getOpponent(),
                    match.getKickoffAt().atZone(zone).toOffsetDateTime(), match.getVenueName(),
                    match.getVenueAddress(), match.getNotes(), match.getStatus(), match.getTeamGoals(),
                    match.getOpponentGoals(), match.getCancellationReason(), match.getVersion());
        }
    }
}

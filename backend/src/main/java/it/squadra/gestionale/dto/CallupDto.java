package it.squadra.gestionale.dto;

import it.squadra.gestionale.dto.MatchDto.MatchResponse;
import it.squadra.gestionale.entities.Availability.AvailabilityStatus;
import it.squadra.gestionale.entities.Callup.CallupStatus;
import it.squadra.gestionale.entities.CallupRevision.CallupRevisionAction;
import it.squadra.gestionale.entities.User.Role;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;

/** DTO delle convocazioni (/api/v1/matches/{id}/callup e /api/v1/callups). */
public final class CallupDto {

    private CallupDto() {
    }

    /**
     * Contenuto della convocazione. {@code meetingAt} è l'orario di ritrovo facoltativo in ora italiana.
     * Su una convocazione già pubblicata il salvataggio crea una nuova revisione (modifica tracciata).
     * {@code confirmWarnings} conferma esplicitamente la convocazione di utenti non disponibili o senza risposta.
     */
    public record CallupRequest(
            LocalDateTime meetingAt,

            @Size(max = 2000, message = "Le note possono avere al massimo 2000 caratteri.")
            String notes,

            @NotNull(message = "Indica i convocati.")
            @Size(max = 100, message = "Troppi convocati.")
            List<@NotNull Long> playerIds,

            Long version,

            Boolean confirmWarnings) {

        public boolean confirmed() {
            return Boolean.TRUE.equals(confirmWarnings);
        }
    }

    public record PublishRequest(
            @NotNull(message = "Versione mancante: ricarica la pagina.")
            Long version,

            Boolean confirmWarnings) {

        public boolean confirmed() {
            return Boolean.TRUE.equals(confirmWarnings);
        }
    }

    public record WithdrawRequest(
            @NotNull(message = "Versione mancante: ricarica la pagina.")
            Long version,

            @Size(max = 500, message = "Il motivo può avere al massimo 500 caratteri.")
            String reason) {
    }

    /** Convocato. {@code availability} è valorizzata solo per Allenatori e Admin. */
    public record CallupPlayerView(Long userId, String firstName, String lastName, Role role,
                                   AvailabilityStatus availability) {
    }

    /**
     * Convocazione. I giocatori vedono solo convocazioni pubblicate o ritirate, senza le disponibilità altrui.
     * {@code warnings}: convocati non disponibili o senza risposta (solo Allenatori e Admin).
     */
    public record CallupResponse(
            Long id,
            Long matchId,
            CallupStatus status,
            OffsetDateTime meetingAt,
            String notes,
            int revision,
            OffsetDateTime publishedAt,
            String publishedBy,
            OffsetDateTime withdrawnAt,
            String withdrawalReason,
            long version,
            boolean convocated,
            List<CallupPlayerView> players,
            List<CallupPlayerView> warnings) {
    }

    public record RevisionResponse(int revision, CallupRevisionAction action, OffsetDateTime createdAt,
                                   String author, String changeSummary) {
    }

    /** Convocazione pubblicata dell'utente per una prossima partita. */
    public record MyCallupResponse(MatchResponse match, OffsetDateTime meetingAt, String notes, int revision,
                                   OffsetDateTime publishedAt) {
    }
}

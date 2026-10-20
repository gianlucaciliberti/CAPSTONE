package it.squadra.gestionale.dto;

import it.squadra.gestionale.entities.Match.MatchStatus;
import it.squadra.gestionale.entities.MatchEvent.MatchEventType;
import it.squadra.gestionale.entities.MatchEvent.TeamSide;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/** DTO di risultato e tabellino (/api/v1/matches/{id}/result e /events). */
public final class ScoresheetDto {

    private ScoresheetDto() {
    }

    public record ResultRequest(
            @NotNull(message = "Indica i gol della squadra.")
            @Min(value = 0, message = "I gol non possono essere negativi.")
            @Max(value = 99, message = "Valore non valido.")
            Integer teamGoals,

            @NotNull(message = "Indica i gol dell'avversario.")
            @Min(value = 0, message = "I gol non possono essere negativi.")
            @Max(value = 99, message = "Valore non valido.")
            Integer opponentGoals,

            @NotNull(message = "Versione mancante: ricarica la pagina.")
            Long version) {
    }

    /**
     * Evento del tabellino. {@code teamSide} è la squadra del giocatore che compie l'evento: un autogol di un nostro
     * giocatore (OWN_GOAL + TEAM) assegna il gol all'avversario. Per i nostri giocatori indicare {@code playerId}
     * (utente registrato) oppure {@code playerName} (senza account); per gli avversari solo {@code playerName}, facoltativo.
     */
    public record EventRequest(
            @NotNull(message = "Indica il tipo di evento.")
            MatchEventType eventType,

            @NotNull(message = "Indica la squadra.")
            TeamSide teamSide,

            Long playerId,

            @Size(max = 120, message = "Il nome può avere al massimo 120 caratteri.")
            String playerName,

            @Min(value = 0, message = "Il minuto deve essere compreso tra 0 e 150.")
            @Max(value = 150, message = "Il minuto deve essere compreso tra 0 e 150.")
            Integer minute,

            @Size(max = 500, message = "Le note possono avere al massimo 500 caratteri.")
            String notes,

            /* Obbligatoria solo in modifica. */
            Long version) {
    }

    /** {@code playerDisplayName}: nome dell'utente registrato oppure nome libero; null se non indicato. */
    public record EventResponse(Long id, MatchEventType eventType, TeamSide teamSide, Long playerId,
                                String playerDisplayName, Integer minute, String notes, long version) {
    }

    /**
     * Tabellino completo. {@code computedTeamGoals}/{@code computedOpponentGoals} sono calcolati dagli eventi;
     * {@code warnings} elenca le incoerenze con il risultato ufficiale (lista vuota = tutto coerente).
     */
    public record ScoresheetResponse(
            Long matchId,
            MatchStatus status,
            Integer teamGoals,
            Integer opponentGoals,
            long matchVersion,
            List<EventResponse> events,
            int computedTeamGoals,
            int computedOpponentGoals,
            boolean consistent,
            List<String> warnings) {
    }
}

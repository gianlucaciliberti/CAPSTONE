package it.squadra.gestionale.services;

import it.squadra.gestionale.dto.ScoresheetDto.EventRequest;
import it.squadra.gestionale.dto.ScoresheetDto.EventResponse;
import it.squadra.gestionale.dto.ScoresheetDto.PlayerStats;
import it.squadra.gestionale.dto.ScoresheetDto.ResultRequest;
import it.squadra.gestionale.dto.ScoresheetDto.ScoresheetResponse;
import it.squadra.gestionale.dto.ScoresheetDto.StatsResponse;
import it.squadra.gestionale.entities.AuditLog.AuditAction;
import it.squadra.gestionale.entities.Match;
import it.squadra.gestionale.entities.Match.MatchStatus;
import it.squadra.gestionale.entities.MatchEvent;
import it.squadra.gestionale.entities.MatchEvent.MatchEventType;
import it.squadra.gestionale.entities.MatchEvent.TeamSide;
import it.squadra.gestionale.entities.User;
import it.squadra.gestionale.exceptions.ApiException;
import it.squadra.gestionale.exceptions.ApiException.ErrorCode;
import it.squadra.gestionale.repositories.MatchEventRepository;
import it.squadra.gestionale.repositories.MatchRepository;
import it.squadra.gestionale.repositories.UserRepository;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Risultato ufficiale e tabellino (solo Admin in scrittura, tutti in lettura).
 * Ogni modifica è registrata nell'audit log con i valori precedenti. Il tabellino si compila solo per partite
 * già iniziate e non annullate; registrare il risultato porta la partita allo stato COMPLETED.
 * Le incoerenze tra risultato ed eventi (es. 3-1 con due soli gol nel tabellino) vengono segnalate, non bloccate:
 * il tabellino può essere incompleto (marcatori non noti).
 * Lo storico (elenco risultati e statistiche) considera solo le partite concluse.
 */
@Service
public class ScoresheetService {

    private final MatchRepository matches;
    private final MatchEventRepository events;
    private final UserRepository users;
    private final MatchService matchService;
    private final AuditService auditService;
    private final Clock clock;

    public ScoresheetService(MatchRepository matches, MatchEventRepository events, UserRepository users,
                             MatchService matchService, AuditService auditService, Clock clock) {
        this.matches = matches;
        this.events = events;
        this.users = users;
        this.matchService = matchService;
        this.auditService = auditService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public ScoresheetResponse get(Long matchId) {
        return toResponse(matchService.get(matchId));
    }

    // --- Storico ---

    /** Partite concluse, dalla più recente. */
    @Transactional(readOnly = true)
    public Page<Match> results(Pageable pageable) {
        return matches.findByStatusOrderByKickoffAtDesc(MatchStatus.COMPLETED, pageable);
    }

    /**
     * Bilancio della squadra e statistiche dei nostri giocatori sulle partite concluse. I giocatori senza account
     * sono raggruppati per nome (senza distinguere maiuscole e spazi). Gli eventi degli avversari non sono inclusi.
     */
    @Transactional(readOnly = true)
    public StatsResponse stats() {
        int wins = 0;
        int draws = 0;
        int losses = 0;
        int goalsFor = 0;
        int goalsAgainst = 0;
        List<Match> completed = matches.findByStatus(MatchStatus.COMPLETED);
        for (Match match : completed) {
            goalsFor += match.getTeamGoals();
            goalsAgainst += match.getOpponentGoals();
            int diff = match.getTeamGoals() - match.getOpponentGoals();
            if (diff > 0) {
                wins++;
            } else if (diff == 0) {
                draws++;
            } else {
                losses++;
            }
        }

        Map<String, PlayerTotals> totals = new LinkedHashMap<>();
        for (MatchEvent event : events.findByMatchStatus(MatchStatus.COMPLETED)) {
            if (event.getTeamSide() != TeamSide.TEAM) {
                continue;
            }
            User player = event.getPlayer();
            String key = player != null ? "id:" + player.getId()
                    : "name:" + event.getPlayerName().trim().toLowerCase(Locale.ROOT);
            PlayerTotals t = totals.computeIfAbsent(key, k -> new PlayerTotals(
                    player == null ? null : player.getId(),
                    player != null ? player.getFullName() : event.getPlayerName().trim()));
            switch (event.getEventType()) {
                case GOAL -> t.goals++;
                case OWN_GOAL -> t.ownGoals++;
                case YELLOW_CARD -> t.yellowCards++;
                case RED_CARD -> t.redCards++;
            }
        }
        List<PlayerStats> players = totals.values().stream()
                .map(t -> new PlayerStats(t.playerId, t.name, t.goals, t.ownGoals, t.yellowCards, t.redCards))
                .sorted(Comparator.comparingInt(PlayerStats::goals).reversed()
                        .thenComparing(PlayerStats::name, String.CASE_INSENSITIVE_ORDER))
                .toList();
        return new StatsResponse(completed.size(), wins, draws, losses, goalsFor, goalsAgainst, players);
    }

    private static final class PlayerTotals {
        private final Long playerId;
        private final String name;
        private int goals;
        private int ownGoals;
        private int yellowCards;
        private int redCards;

        private PlayerTotals(Long playerId, String name) {
            this.playerId = playerId;
            this.name = name;
        }
    }

    // --- Risultato ---

    @Transactional
    public ScoresheetResponse recordResult(Long matchId, ResultRequest request, Long actorId, String ip) {
        Match match = editableMatch(matchId);
        if (!Objects.equals(request.version(), match.getVersion())) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.CONCURRENT_MODIFICATION,
                    "La partita è stata modificata da qualcun altro. Ricarica la pagina e riprova.");
        }
        Map<String, Object> before = result(match);
        User actor = users.getReferenceById(actorId);
        match.recordResult(request.teamGoals(), request.opponentGoals(), actor, clock.instant());
        Map<String, Object> after = result(match);
        if (!before.equals(after)) {
            auditService.record(actor, AuditAction.MATCH_RESULT_RECORDED, "MATCH", matchId,
                    "Risultato " + request.teamGoals() + "-" + request.opponentGoals() + " contro "
                            + match.getOpponent(), Map.of("before", before, "after", after), ip);
        }
        matches.flush();
        return toResponse(match);
    }

    // --- Eventi ---

    @Transactional
    public ScoresheetResponse addEvent(Long matchId, EventRequest request, Long actorId, String ip) {
        Match match = editableMatch(matchId);
        User player = validatedPlayer(request);
        User actor = users.getReferenceById(actorId);
        MatchEvent event = events.save(new MatchEvent(match, request.eventType(), request.teamSide(), player,
                playerName(request), request.minute(), blankToNull(request.notes()), actor));
        auditService.record(actor, AuditAction.MATCH_EVENT_CREATED, "MATCH_EVENT", event.getId(),
                "Evento " + request.eventType() + " aggiunto al tabellino contro " + match.getOpponent(),
                Map.of("matchId", matchId, "after", describe(event)), ip);
        events.flush();
        return toResponse(match);
    }

    @Transactional
    public ScoresheetResponse updateEvent(Long matchId, Long eventId, EventRequest request, Long actorId, String ip) {
        Match match = editableMatch(matchId);
        MatchEvent event = findEvent(matchId, eventId);
        if (request.version() == null || request.version() != event.getVersion()) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.CONCURRENT_MODIFICATION,
                    "L'evento è stato modificato da qualcun altro. Ricarica la pagina e riprova.");
        }
        User player = validatedPlayer(request);
        User actor = users.getReferenceById(actorId);
        Map<String, Object> before = describe(event);
        event.update(request.eventType(), request.teamSide(), player, playerName(request), request.minute(),
                blankToNull(request.notes()), actor);
        Map<String, Object> after = describe(event);
        if (!before.equals(after)) {
            auditService.record(actor, AuditAction.MATCH_EVENT_UPDATED, "MATCH_EVENT", eventId,
                    "Evento del tabellino modificato contro " + match.getOpponent(),
                    Map.of("matchId", matchId, "before", before, "after", after), ip);
        }
        events.flush();
        return toResponse(match);
    }

    /** Elimina un evento inserito per errore; i suoi dati restano nell'audit log. */
    @Transactional
    public ScoresheetResponse deleteEvent(Long matchId, Long eventId, Long actorId, String ip) {
        Match match = editableMatch(matchId);
        MatchEvent event = findEvent(matchId, eventId);
        User actor = users.getReferenceById(actorId);
        auditService.record(actor, AuditAction.MATCH_EVENT_DELETED, "MATCH_EVENT", eventId,
                "Evento del tabellino eliminato contro " + match.getOpponent(),
                Map.of("matchId", matchId, "before", describe(event)), ip);
        events.delete(event);
        events.flush();
        return toResponse(match);
    }

    // --- Regole ---

    private Match editableMatch(Long matchId) {
        Match match = matchService.get(matchId);
        if (match.getStatus() == MatchStatus.CANCELLED) {
            throw ApiException.businessRule("La partita è stata annullata: non ha risultato né tabellino.");
        }
        if (match.getKickoffAt().isAfter(clock.instant())) {
            throw ApiException.businessRule("La partita non è ancora iniziata.");
        }
        return match;
    }

    /**
     * Nostri giocatori: utente registrato (playerId) oppure nome libero (senza account), almeno uno dei due.
     * Avversari: mai un utente; nome facoltativo.
     */
    private User validatedPlayer(EventRequest request) {
        boolean hasName = request.playerName() != null && !request.playerName().isBlank();
        if (request.teamSide() == TeamSide.OPPONENT) {
            if (request.playerId() != null) {
                throw ApiException.businessRule("I giocatori avversari non hanno un account: indica solo il nome.");
            }
            return null;
        }
        if (request.playerId() != null && hasName) {
            throw ApiException.businessRule("Indica il giocatore registrato oppure il nome, non entrambi.");
        }
        if (request.playerId() == null && !hasName) {
            throw ApiException.businessRule("Indica il giocatore della squadra.");
        }
        return request.playerId() == null ? null : users.findById(request.playerId())
                .orElseThrow(() -> ApiException.businessRule("Giocatore non trovato."));
    }

    private MatchEvent findEvent(Long matchId, Long eventId) {
        return events.findById(eventId)
                .filter(e -> e.getMatch().getId().equals(matchId))
                .orElseThrow(() -> ApiException.notFound("Evento non trovato."));
    }

    // --- Risposta e coerenza ---

    private ScoresheetResponse toResponse(Match match) {
        List<MatchEvent> list = events.findByMatchIdOrderByMinuteAscIdAsc(match.getId());
        int computedTeam = (int) list.stream().filter(MatchEvent::countsForTeam).count();
        int computedOpponent = (int) list.stream().filter(MatchEvent::countsForOpponent).count();
        List<String> warnings = warnings(match, list, computedTeam, computedOpponent);
        return new ScoresheetResponse(match.getId(), match.getStatus(), match.getTeamGoals(), match.getOpponentGoals(),
                match.getVersion(), list.stream().map(ScoresheetService::toEventResponse).toList(),
                computedTeam, computedOpponent, warnings.isEmpty(), warnings);
    }

    /** Incoerenze tra risultato ufficiale ed eventi registrati. */
    static List<String> warnings(Match match, List<MatchEvent> list, int computedTeam, int computedOpponent) {
        List<String> warnings = new ArrayList<>();
        if (match.getTeamGoals() == null) {
            if (!list.isEmpty()) {
                warnings.add("Il tabellino contiene eventi ma il risultato ufficiale non è ancora registrato.");
            }
            return warnings;
        }
        if (computedTeam != match.getTeamGoals()) {
            warnings.add("Gol della squadra: risultato " + match.getTeamGoals() + ", nel tabellino " + computedTeam + ".");
        }
        if (computedOpponent != match.getOpponentGoals()) {
            warnings.add("Gol dell'avversario: risultato " + match.getOpponentGoals() + ", nel tabellino "
                    + computedOpponent + ".");
        }
        // Più espulsioni o doppia ammonizione senza espulsione per lo stesso giocatore registrato.
        Map<Long, Integer> yellows = new HashMap<>();
        Map<Long, Integer> reds = new HashMap<>();
        for (MatchEvent event : list) {
            if (event.getPlayer() == null) {
                continue;
            }
            Long id = event.getPlayer().getId();
            if (event.getEventType() == MatchEventType.YELLOW_CARD) {
                yellows.merge(id, 1, Integer::sum);
            } else if (event.getEventType() == MatchEventType.RED_CARD) {
                reds.merge(id, 1, Integer::sum);
            }
        }
        reds.forEach((id, count) -> {
            if (count > 1) {
                warnings.add("Un giocatore risulta espulso più di una volta.");
            }
        });
        yellows.forEach((id, count) -> {
            if (count > 2 || (count == 2 && !reds.containsKey(id))) {
                warnings.add("Un giocatore ha due ammonizioni ma nessuna espulsione.");
            }
        });
        return warnings;
    }

    private static EventResponse toEventResponse(MatchEvent event) {
        String name = event.getPlayer() != null ? event.getPlayer().getFullName() : event.getPlayerName();
        return new EventResponse(event.getId(), event.getEventType(), event.getTeamSide(),
                event.getPlayer() == null ? null : event.getPlayer().getId(), name, event.getMinute(),
                event.getNotes(), event.getVersion());
    }

    private static Map<String, Object> result(Match match) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("status", match.getStatus().name());
        data.put("teamGoals", match.getTeamGoals() == null ? "" : match.getTeamGoals());
        data.put("opponentGoals", match.getOpponentGoals() == null ? "" : match.getOpponentGoals());
        return data;
    }

    private static Map<String, Object> describe(MatchEvent event) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("eventType", event.getEventType().name());
        data.put("teamSide", event.getTeamSide().name());
        data.put("player", event.getPlayer() != null ? event.getPlayer().getFullName()
                : event.getPlayerName() == null ? "" : event.getPlayerName());
        data.put("minute", event.getMinute() == null ? "" : event.getMinute());
        data.put("notes", event.getNotes() == null ? "" : event.getNotes());
        return data;
    }

    private static String playerName(EventRequest request) {
        return blankToNull(request.playerName());
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}

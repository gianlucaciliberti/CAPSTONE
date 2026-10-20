package it.squadra.gestionale.services;

import it.squadra.gestionale.dto.CallupDto.CallupPlayerView;
import it.squadra.gestionale.dto.CallupDto.CallupRequest;
import it.squadra.gestionale.dto.CallupDto.CallupResponse;
import it.squadra.gestionale.dto.CallupDto.MyCallupResponse;
import it.squadra.gestionale.dto.CallupDto.RevisionResponse;
import it.squadra.gestionale.dto.MatchDto.MatchResponse;
import it.squadra.gestionale.entities.AuditLog.AuditAction;
import it.squadra.gestionale.entities.Availability;
import it.squadra.gestionale.entities.Availability.AvailabilityStatus;
import it.squadra.gestionale.entities.Callup;
import it.squadra.gestionale.entities.Callup.CallupStatus;
import it.squadra.gestionale.entities.CallupPlayer;
import it.squadra.gestionale.entities.CallupRevision;
import it.squadra.gestionale.entities.CallupRevision.CallupRevisionAction;
import it.squadra.gestionale.entities.Match;
import it.squadra.gestionale.entities.Match.MatchStatus;
import it.squadra.gestionale.entities.User;
import it.squadra.gestionale.exceptions.ApiException;
import it.squadra.gestionale.exceptions.ApiException.ErrorCode;
import it.squadra.gestionale.repositories.AvailabilityRepository;
import it.squadra.gestionale.repositories.CallupPlayerRepository;
import it.squadra.gestionale.repositories.CallupRepository;
import it.squadra.gestionale.repositories.CallupRevisionRepository;
import it.squadra.gestionale.repositories.UserRepository;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * Convocazioni (una per partita), preparate e pubblicate da Allenatori e Admin.
 * <ul>
 *   <li>DRAFT: bozza visibile solo ad Allenatori e Admin.</li>
 *   <li>PUBLISHED: visibile a tutti; blocca le disponibilità. Ogni ulteriore salvataggio è una modifica
 *       tracciata con una nuova revisione.</li>
 *   <li>WITHDRAWN: ritirata; resta nello storico. Un nuovo salvataggio la riporta in bozza.</li>
 * </ul>
 * Ogni pubblicazione, modifica e ritiro crea una revisione immutabile (snapshot) e una voce di audit.
 * Convocare utenti non disponibili o senza risposta è possibile solo con una conferma esplicita.
 * Nulla viene cancellato: i convocati rimossi restano con data e autore della rimozione.
 */
@Service
public class CallupService {

    private final CallupRepository callups;
    private final CallupPlayerRepository callupPlayers;
    private final CallupRevisionRepository revisions;
    private final AvailabilityRepository availabilities;
    private final UserRepository users;
    private final MatchService matchService;
    private final AuditService auditService;
    private final EntityManager entityManager;
    private final JsonMapper jsonMapper;
    private final Clock clock;

    public CallupService(CallupRepository callups, CallupPlayerRepository callupPlayers,
                         CallupRevisionRepository revisions, AvailabilityRepository availabilities,
                         UserRepository users, MatchService matchService, AuditService auditService,
                         EntityManager entityManager, JsonMapper jsonMapper, Clock clock) {
        this.callups = callups;
        this.callupPlayers = callupPlayers;
        this.revisions = revisions;
        this.availabilities = availabilities;
        this.users = users;
        this.matchService = matchService;
        this.auditService = auditService;
        this.entityManager = entityManager;
        this.jsonMapper = jsonMapper;
        this.clock = clock;
    }

    // --- Lettura ---

    /**
     * Convocazione di una partita vista dall'utente. Chi non è Allenatore/Admin vede solo convocazioni
     * pubblicate o ritirate (le bozze risultano inesistenti) e non vede le disponibilità altrui.
     */
    @Transactional(readOnly = true)
    public CallupResponse view(Long matchId, Long viewerId, boolean staff) {
        Callup callup = callups.findByMatchId(matchId)
                .filter(c -> staff || c.getStatus() != CallupStatus.DRAFT)
                .orElseThrow(() -> ApiException.notFound("Convocazione non ancora disponibile."));
        return toResponse(callup, viewerId, staff);
    }

    @Transactional(readOnly = true)
    public List<RevisionResponse> revisions(Long matchId) {
        Callup callup = callups.findByMatchId(matchId)
                .orElseThrow(() -> ApiException.notFound("Convocazione non trovata."));
        return revisions.findByCallupIdOrderByRevisionNumberDesc(callup.getId()).stream()
                .map(r -> new RevisionResponse(r.getRevisionNumber(), r.getAction(), zoned(r.getCreatedAt()),
                        r.getAuthor().getFullName(), r.getChangeSummary()))
                .toList();
    }

    /** Convocazioni pubblicate dell'utente per le prossime partite (da oggi in poi). */
    @Transactional(readOnly = true)
    public List<MyCallupResponse> mine(Long userId) {
        ZoneId zone = matchService.zone();
        Instant startOfToday = LocalDate.now(clock.withZone(zone)).atStartOfDay(zone).toInstant();
        return callupPlayers.findUpcomingPublishedForUser(userId, startOfToday).stream()
                .map(CallupPlayer::getCallup)
                .map(c -> new MyCallupResponse(MatchResponse.from(c.getMatch(), zone), zoned(c.getMeetingAt()),
                        c.getNotes(), c.getCurrentRevision(), zoned(c.getPublishedAt())))
                .toList();
    }

    // --- Modifica ---

    /**
     * Salva contenuto e convocati. Bozza (o nuova/ritirata): resta in bozza, nessuna notifica.
     * Pubblicata: le modifiche sono immediatamente visibili e registrate come revisione UPDATED.
     */
    @Transactional
    public CallupResponse save(Long matchId, CallupRequest request, Long actorId, String ip) {
        Match match = editableMatch(matchId);
        User actor = users.getReferenceById(actorId);
        Callup callup = callups.findByMatchId(matchId).orElse(null);
        if (callup == null) {
            if (request.version() != null) {
                throw conflict();
            }
            callup = callups.save(new Callup(match, actor));
        } else {
            ensureVersion(callup, request.version());
        }

        List<User> selected = activeUsers(request.playerIds());
        boolean published = callup.getStatus() == CallupStatus.PUBLISHED;
        if (published) {
            if (selected.isEmpty()) {
                throw ApiException.businessRule(
                        "Una convocazione pubblicata deve avere almeno un convocato: per annullarla usa il ritiro.");
            }
            requireConfirmation(match, selected, request.confirmed());
        }

        Instant now = clock.instant();
        Map<String, Object> before = published ? snapshot(callup) : null;
        callup.setMeetingAt(request.meetingAt() == null ? null : toInstant(request.meetingAt()));
        callup.setNotes(blankToNull(request.notes()));
        callup.setUpdatedBy(actor);
        if (callup.getStatus() == CallupStatus.WITHDRAWN) {
            callup.setStatus(CallupStatus.DRAFT);
        }
        syncPlayers(callup, selected, actor, now);

        if (published) {
            Map<String, Object> after = snapshot(callup);
            if (!after.equals(before)) {
                String summary = changeSummary(before, after);
                commitRevision(callup, CallupRevisionAction.UPDATED, summary, actor, now);
                auditService.record(actor, AuditAction.CALLUP_UPDATED, "CALLUP", callup.getId(),
                        "Convocazione modificata (revisione " + callup.getCurrentRevision() + "): " + summary,
                        Map.of("matchId", matchId, "revision", callup.getCurrentRevision(),
                                "before", before, "after", after), ip);
            }
        }
        return flushAndView(callup, actorId);
    }

    @Transactional
    public CallupResponse publish(Long matchId, Long version, boolean confirmWarnings, Long actorId, String ip) {
        Match match = editableMatch(matchId);
        Callup callup = callups.findByMatchId(matchId)
                .orElseThrow(() -> ApiException.notFound("Prepara prima la convocazione."));
        ensureVersion(callup, version);
        if (callup.getStatus() != CallupStatus.DRAFT) {
            throw ApiException.businessRule("Si possono pubblicare solo le convocazioni in bozza.");
        }
        List<User> convocated = callup.getActivePlayers().stream().map(CallupPlayer::getUser).toList();
        if (convocated.isEmpty()) {
            throw ApiException.businessRule("Seleziona almeno un convocato prima di pubblicare.");
        }
        requireConfirmation(match, convocated, confirmWarnings);

        User actor = users.getReferenceById(actorId);
        Instant now = clock.instant();
        callup.markPublished(actor, now);
        commitRevision(callup, CallupRevisionAction.PUBLISHED, convocated.size() + " convocati", actor, now);
        auditService.record(actor, AuditAction.CALLUP_PUBLISHED, "CALLUP", callup.getId(),
                "Convocazione pubblicata per la partita contro " + match.getOpponent(),
                Map.of("matchId", matchId, "revision", callup.getCurrentRevision(), "players", convocated.size()), ip);
        return flushAndView(callup, actorId);
    }

    @Transactional
    public CallupResponse withdraw(Long matchId, Long version, String reason, Long actorId, String ip) {
        Match match = matchService.get(matchId);
        Callup callup = callups.findByMatchId(matchId)
                .orElseThrow(() -> ApiException.notFound("Convocazione non trovata."));
        ensureVersion(callup, version);
        if (callup.getStatus() != CallupStatus.PUBLISHED) {
            throw ApiException.businessRule("Si possono ritirare solo le convocazioni pubblicate.");
        }
        User actor = users.getReferenceById(actorId);
        Instant now = clock.instant();
        callup.markWithdrawn(blankToNull(reason), actor, now);
        commitRevision(callup, CallupRevisionAction.WITHDRAWN, reason == null || reason.isBlank()
                ? "Convocazione ritirata" : "Convocazione ritirata: " + reason.trim(), actor, now);
        auditService.record(actor, AuditAction.CALLUP_WITHDRAWN, "CALLUP", callup.getId(),
                "Convocazione ritirata per la partita contro " + match.getOpponent(),
                Map.of("matchId", matchId, "revision", callup.getCurrentRevision(),
                        "reason", reason == null ? "" : reason), ip);
        return flushAndView(callup, actorId);
    }

    // --- Supporto ---

    private Match editableMatch(Long matchId) {
        Match match = matchService.get(matchId);
        if (match.getStatus() != MatchStatus.SCHEDULED) {
            throw ApiException.businessRule("Le convocazioni si gestiscono solo per partite programmate.");
        }
        if (!match.getKickoffAt().isAfter(clock.instant())) {
            throw ApiException.businessRule("La partita è già iniziata: la convocazione non è più modificabile.");
        }
        return match;
    }

    /** Utenti attivi corrispondenti agli id richiesti (ordine e duplicati ignorati); errore se qualcuno non lo è. */
    private List<User> activeUsers(List<Long> ids) {
        Set<Long> unique = new LinkedHashSet<>(ids);
        List<User> found = users.findAllById(unique).stream().filter(User::isActive).toList();
        if (found.size() != unique.size()) {
            throw ApiException.businessRule("Alcuni convocati non esistono o non hanno un account attivo.");
        }
        return found;
    }

    /** Aggiunge i nuovi convocati e rimuove (logicamente) quelli non più selezionati. */
    private void syncPlayers(Callup callup, List<User> selected, User actor, Instant now) {
        Set<Long> selectedIds = selected.stream().map(User::getId).collect(Collectors.toSet());
        Set<Long> currentIds = new LinkedHashSet<>();
        for (CallupPlayer player : callup.getActivePlayers()) {
            if (selectedIds.contains(player.getUser().getId())) {
                currentIds.add(player.getUser().getId());
            } else {
                player.remove(actor, now);
            }
        }
        for (User user : selected) {
            if (!currentIds.contains(user.getId())) {
                CallupPlayer added = new CallupPlayer(callup, user, actor, now);
                callup.getPlayers().add(added);
                callupPlayers.save(added);
            }
        }
    }

    /**
     * Registra una nuova revisione: numera le aggiunte/rimozioni non ancora pubblicate e salva lo snapshot.
     * Gli indicatori added/removedInRevision serviranno a notificare solo gli utenti interessati.
     */
    private void commitRevision(Callup callup, CallupRevisionAction action, String summary, User author,
                                Instant now) {
        int revision = callup.getCurrentRevision() + 1;
        for (CallupPlayer player : callup.getPlayers()) {
            if (player.isActive() && player.getAddedInRevision() == null) {
                player.setAddedInRevision(revision);
            }
            if (!player.isActive() && player.getRemovedInRevision() == null) {
                player.setRemovedInRevision(revision);
            }
        }
        callup.setCurrentRevision(revision);
        Map<String, Object> snapshot = new LinkedHashMap<>(snapshot(callup));
        snapshot.put("revision", revision);
        snapshot.put("action", action.name());
        revisions.save(new CallupRevision(callup, revision, action, jsonMapper.writeValueAsString(snapshot),
                summary, author, now));
    }

    /** Conferma obbligatoria quando si convocano utenti non disponibili o senza risposta. */
    private void requireConfirmation(Match match, List<User> convocated, boolean confirmed) {
        if (confirmed) {
            return;
        }
        List<CallupPlayerView> warnings = warningsFor(match.getId(), convocated);
        if (!warnings.isEmpty()) {
            String names = warnings.stream().map(w -> w.firstName() + " " + w.lastName() + " ("
                    + (w.availability() == AvailabilityStatus.UNAVAILABLE ? "non disponibile" : "senza risposta") + ")")
                    .collect(Collectors.joining(", "));
            throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, ErrorCode.CALLUP_WARNINGS,
                    "Conferma la convocazione di: " + names + ".");
        }
    }

    private List<CallupPlayerView> warningsFor(Long matchId, List<User> convocated) {
        Map<Long, AvailabilityStatus> answers = availabilityMap(matchId);
        return convocated.stream()
                .map(u -> view(u, answers.getOrDefault(u.getId(), AvailabilityStatus.PENDING)))
                .filter(v -> v.availability() != AvailabilityStatus.AVAILABLE)
                .sorted(byName())
                .toList();
    }

    private Map<Long, AvailabilityStatus> availabilityMap(Long matchId) {
        return availabilities.findByMatchId(matchId).stream()
                .collect(Collectors.toMap(a -> a.getUser().getId(), Availability::getStatus));
    }

    private CallupResponse flushAndView(Callup callup, Long viewerId) {
        // Ogni operazione modifica la riga della convocazione: la versione restituita è già quella nuova.
        callup.touch(users.getReferenceById(viewerId), clock.instant());
        entityManager.flush();
        return toResponse(callup, viewerId, true);
    }

    private CallupResponse toResponse(Callup callup, Long viewerId, boolean staff) {
        Map<Long, AvailabilityStatus> answers = staff ? availabilityMap(callup.getMatch().getId()) : Map.of();
        List<User> active = callup.getActivePlayers().stream().map(CallupPlayer::getUser).toList();
        List<CallupPlayerView> players = active.stream()
                .map(u -> view(u, staff ? answers.getOrDefault(u.getId(), AvailabilityStatus.PENDING) : null))
                .sorted(byName())
                .toList();
        List<CallupPlayerView> warnings = staff
                ? players.stream().filter(p -> p.availability() != AvailabilityStatus.AVAILABLE).toList()
                : List.of();
        boolean convocated = active.stream().anyMatch(u -> u.getId().equals(viewerId));
        return new CallupResponse(callup.getId(), callup.getMatch().getId(), callup.getStatus(),
                zoned(callup.getMeetingAt()), callup.getNotes(), callup.getCurrentRevision(),
                zoned(callup.getPublishedAt()), callup.getPublishedBy() == null ? null : callup.getPublishedBy().getFullName(),
                zoned(callup.getWithdrawnAt()), callup.getWithdrawalReason(), callup.getVersion(), convocated,
                players, warnings);
    }

    private static CallupPlayerView view(User user, AvailabilityStatus availability) {
        return new CallupPlayerView(user.getId(), user.getFirstName(), user.getLastName(), user.getRole(), availability);
    }

    private static Comparator<CallupPlayerView> byName() {
        return Comparator.comparing(CallupPlayerView::lastName, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(CallupPlayerView::firstName, String.CASE_INSENSITIVE_ORDER);
    }

    /** Dati confrontabili della convocazione: dettagli della partita, ritrovo, note, convocati. */
    private Map<String, Object> snapshot(Callup callup) {
        Match match = callup.getMatch();
        ZoneId zone = matchService.zone();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("opponent", match.getOpponent());
        data.put("kickoff", match.getKickoffAt().atZone(zone).toLocalDateTime().toString());
        data.put("venue", match.getVenueName() + ", " + match.getVenueAddress());
        data.put("meetingAt", callup.getMeetingAt() == null ? "" : callup.getMeetingAt().atZone(zone)
                .toLocalDateTime().toString());
        data.put("notes", callup.getNotes() == null ? "" : callup.getNotes());
        data.put("players", callup.getActivePlayers().stream()
                .map(p -> Map.of("id", p.getUser().getId(), "name", p.getUser().getFullName()))
                .sorted(Comparator.comparing(p -> (Long) p.get("id")))
                .toList());
        return data;
    }

    @SuppressWarnings("unchecked")
    private static String changeSummary(Map<String, Object> before, Map<String, Object> after) {
        List<String> changes = new ArrayList<>();
        Function<Object, Set<String>> names = list -> ((List<Map<String, Object>>) list).stream()
                .map(p -> (String) p.get("name")).collect(Collectors.toCollection(LinkedHashSet::new));
        Set<String> oldPlayers = names.apply(before.get("players"));
        Set<String> newPlayers = names.apply(after.get("players"));
        List<String> added = newPlayers.stream().filter(n -> !oldPlayers.contains(n)).toList();
        List<String> removed = oldPlayers.stream().filter(n -> !newPlayers.contains(n)).toList();
        if (!added.isEmpty()) {
            changes.add("aggiunti " + String.join(", ", added));
        }
        if (!removed.isEmpty()) {
            changes.add("rimossi " + String.join(", ", removed));
        }
        if (!Objects.equals(before.get("meetingAt"), after.get("meetingAt"))) {
            changes.add("orario di ritrovo");
        }
        if (!Objects.equals(before.get("notes"), after.get("notes"))) {
            changes.add("note");
        }
        String summary = changes.isEmpty() ? "dettagli" : String.join("; ", changes);
        return summary.length() > 1000 ? summary.substring(0, 997) + "..." : summary;
    }

    private static void ensureVersion(Callup callup, Long version) {
        if (version == null || !Objects.equals(version, callup.getVersion())) {
            throw conflict();
        }
    }

    private static ApiException conflict() {
        return new ApiException(HttpStatus.CONFLICT, ErrorCode.CONCURRENT_MODIFICATION,
                "La convocazione è stata modificata da qualcun altro. Ricarica la pagina e riprova.");
    }

    private Instant toInstant(LocalDateTime local) {
        return local.atZone(matchService.zone()).toInstant();
    }

    private OffsetDateTime zoned(Instant instant) {
        return instant == null ? null : instant.atZone(matchService.zone()).toOffsetDateTime();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}

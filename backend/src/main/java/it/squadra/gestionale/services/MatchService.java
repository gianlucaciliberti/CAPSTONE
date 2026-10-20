package it.squadra.gestionale.services;

import it.squadra.gestionale.config.AppProperties;
import it.squadra.gestionale.dto.MatchDto.MatchRequest;
import it.squadra.gestionale.entities.AuditLog.AuditAction;
import it.squadra.gestionale.entities.Match;
import it.squadra.gestionale.entities.Match.MatchStatus;
import it.squadra.gestionale.entities.User;
import it.squadra.gestionale.entities.User.Role;
import it.squadra.gestionale.exceptions.ApiException;
import it.squadra.gestionale.exceptions.ApiException.ErrorCode;
import it.squadra.gestionale.repositories.MatchRepository;
import it.squadra.gestionale.repositories.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Calendario delle partite. Creazione, modifica e annullamento sono riservati all'Admin (controllo sul controller)
 * e registrati nell'audit log. Le partite annullate restano nello storico.
 * Le date sono interpretate nel fuso della squadra ({@code app.timezone}, Europe/Rome).
 */
@Service
public class MatchService {

    private static final Logger log = LoggerFactory.getLogger(MatchService.class);

    private final MatchRepository matches;
    private final UserRepository users;
    private final AuditService auditService;
    private final Clock clock;
    private final ZoneId zone;

    public MatchService(MatchRepository matches, UserRepository users, AuditService auditService, Clock clock,
                        AppProperties properties) {
        this.matches = matches;
        this.users = users;
        this.auditService = auditService;
        this.clock = clock;
        this.zone = properties.timezone();
    }

    public ZoneId zone() {
        return zone;
    }

    @Transactional(readOnly = true)
    public Match get(Long id) {
        return matches.findById(id).orElseThrow(() -> ApiException.notFound("Partita non trovata."));
    }

    /** Tutte le partite del mese (in ora italiana), comprese quelle annullate. */
    @Transactional(readOnly = true)
    public List<Match> month(YearMonth month) {
        Instant from = month.atDay(1).atStartOfDay(zone).toInstant();
        Instant to = month.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant();
        return matches.findByKickoffAtGreaterThanEqualAndKickoffAtLessThanOrderByKickoffAtAsc(from, to);
    }

    /** Partite programmate a partire dall'inizio di oggi (così resta visibile anche quella in corso). */
    @Transactional(readOnly = true)
    public Page<Match> upcoming(Pageable pageable) {
        Instant startOfToday = LocalDate.now(clock.withZone(zone)).atStartOfDay(zone).toInstant();
        return matches.findByStatusAndKickoffAtGreaterThanEqualOrderByKickoffAtAsc(MatchStatus.SCHEDULED,
                startOfToday, pageable);
    }

    @Transactional
    public Match create(MatchRequest request, Long actorId, String ip) {
        User actor = users.getReferenceById(actorId);
        Match match = matches.save(new Match(request.opponent().trim(), toInstant(request.kickoff()),
                request.venueName().trim(), request.venueAddress().trim(), blankToNull(request.notes()), actor));
        auditService.record(actor, AuditAction.MATCH_CREATED, "MATCH", match.getId(),
                "Partita creata contro " + match.getOpponent(), snapshot(match), ip);
        return match;
    }

    @Transactional
    public Match update(Long id, MatchRequest request, Long actorId, String ip) {
        Match match = get(id);
        ensureVersion(match, request.version());
        if (match.getStatus() == MatchStatus.CANCELLED) {
            throw ApiException.businessRule("Una partita annullata non può essere modificata.");
        }
        Map<String, Object> before = snapshot(match);
        User actor = users.getReferenceById(actorId);
        match.updateDetails(request.opponent().trim(), toInstant(request.kickoff()), request.venueName().trim(),
                request.venueAddress().trim(), blankToNull(request.notes()), actor);
        Map<String, Object> after = snapshot(match);
        if (!before.equals(after)) {
            auditService.record(actor, AuditAction.MATCH_UPDATED, "MATCH", match.getId(),
                    "Partita modificata contro " + match.getOpponent(), Map.of("before", before, "after", after), ip);
        }
        // Flush esplicito: la risposta deve contenere la nuova versione per le modifiche successive.
        matches.flush();
        return match;
    }

    @Transactional
    public Match cancel(Long id, String reason, Long version, Long actorId, String ip) {
        Match match = get(id);
        ensureVersion(match, version);
        if (match.getStatus() != MatchStatus.SCHEDULED) {
            throw ApiException.businessRule("Si possono annullare solo le partite programmate.");
        }
        User actor = users.getReferenceById(actorId);
        match.cancel(blankToNull(reason), actor, clock.instant());
        Map<String, Object> details = new LinkedHashMap<>(snapshot(match));
        details.put("reason", reason == null ? "" : reason);
        auditService.record(actor, AuditAction.MATCH_CANCELLED, "MATCH", match.getId(),
                "Partita annullata contro " + match.getOpponent(), details, ip);
        matches.flush();
        return match;
    }

    /**
     * Solo profilo dev: alcune partite dimostrative attorno a oggi, se il calendario è vuoto
     * ed esiste un Admin (creato dai dati demo o dal primo avvio).
     */
    @Transactional
    public void createDemoMatchesIfEmpty() {
        if (matches.count() > 0) {
            return;
        }
        User admin = users.findAll().stream().filter(u -> u.getRole() == Role.ADMIN).findFirst().orElse(null);
        if (admin == null) {
            return;
        }
        LocalDate today = LocalDate.now(clock.withZone(zone));
        record Demo(String opponent, int days, LocalTime time, String venue, String address) {
        }
        List<Demo> demo = List.of(
                new Demo("Real Borgata", -10, LocalTime.of(20, 30), "Centro sportivo Comunale", "Via dello Sport 1"),
                new Demo("Atletico Quartiere", 3, LocalTime.of(21, 0), "Campo Parrocchiale", "Piazza della Chiesa 4"),
                new Demo("Sporting Amici", 10, LocalTime.of(15, 30), "Centro sportivo Comunale", "Via dello Sport 1"),
                new Demo("Dinamo Bar Centrale", 17, LocalTime.of(20, 45), "Stadio Comunale", "Viale dei Tigli 12"),
                new Demo("Polisportiva Riva", 31, LocalTime.of(10, 30), "Campo Riva", "Lungofiume 8"));
        for (Demo d : demo) {
            Instant kickoff = today.plusDays(d.days()).atTime(d.time()).atZone(zone).toInstant();
            matches.save(new Match(d.opponent(), kickoff, d.venue(), d.address(), null, admin));
        }
        log.warn("Profilo dev: create {} partite dimostrative", demo.size());
    }

    /**
     * Converte data e ora locali in istante. Nel buco dell'ora legale (es. 02:30 dell'ultima domenica di marzo)
     * {@code atZone} sposta in avanti di un'ora; nell'ora ripetuta di ottobre sceglie la prima occorrenza.
     */
    private Instant toInstant(LocalDateTime local) {
        return local.atZone(zone).toInstant();
    }

    private static void ensureVersion(Match match, Long version) {
        if (version == null) {
            throw ApiException.businessRule("Versione mancante: ricarica la pagina e riprova.");
        }
        if (!Objects.equals(version, match.getVersion())) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.CONCURRENT_MODIFICATION,
                    "La partita è stata modificata da qualcun altro. Ricarica la pagina e riprova.");
        }
    }

    private Map<String, Object> snapshot(Match match) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("opponent", match.getOpponent());
        data.put("kickoff", match.getKickoffAt().atZone(zone).toLocalDateTime().toString());
        data.put("venueName", match.getVenueName());
        data.put("venueAddress", match.getVenueAddress());
        data.put("notes", match.getNotes() == null ? "" : match.getNotes());
        data.put("status", match.getStatus().name());
        return data;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}

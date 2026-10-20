package it.squadra.gestionale.services;

import it.squadra.gestionale.dto.AvailabilityDto.AvailabilityRequest;
import it.squadra.gestionale.dto.AvailabilityDto.MyAvailabilityResponse;
import it.squadra.gestionale.dto.AvailabilityDto.PlayerAvailability;
import it.squadra.gestionale.dto.AvailabilityDto.TeamAvailabilityResponse;
import it.squadra.gestionale.dto.AvailabilityDto.UpcomingAvailability;
import it.squadra.gestionale.dto.MatchDto.MatchResponse;
import it.squadra.gestionale.entities.AuditLog.AuditAction;
import it.squadra.gestionale.entities.Availability;
import it.squadra.gestionale.entities.Availability.AvailabilityStatus;
import it.squadra.gestionale.entities.Callup.CallupStatus;
import it.squadra.gestionale.entities.Match;
import it.squadra.gestionale.entities.Match.MatchStatus;
import it.squadra.gestionale.entities.User;
import it.squadra.gestionale.entities.User.AccountStatus;
import it.squadra.gestionale.exceptions.ApiException;
import it.squadra.gestionale.repositories.AvailabilityRepository;
import it.squadra.gestionale.repositories.CallupRepository;
import it.squadra.gestionale.repositories.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Disponibilità alle partite.
 * <ul>
 *   <li>Ogni utente modifica solo la propria disponibilità, finché la partita è programmata e futura
 *       e la convocazione non è pubblicata.</li>
 *   <li>Dopo la pubblicazione solo l'Admin può correggerla (correzione amministrativa esplicita, con audit).</li>
 *   <li>Allenatori e Admin vedono il riepilogo della squadra; nessuna risposta = "in attesa" (PENDING).</li>
 * </ul>
 */
@Service
public class AvailabilityService {

    private final AvailabilityRepository availabilities;
    private final CallupRepository callups;
    private final UserRepository users;
    private final MatchService matchService;
    private final AuditService auditService;
    private final Clock clock;

    public AvailabilityService(AvailabilityRepository availabilities, CallupRepository callups, UserRepository users,
                               MatchService matchService, AuditService auditService, Clock clock) {
        this.availabilities = availabilities;
        this.callups = callups;
        this.users = users;
        this.matchService = matchService;
        this.auditService = auditService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public MyAvailabilityResponse mine(Long matchId, Long userId) {
        Match match = matchService.get(matchId);
        return toMine(match, availabilities.findByMatchIdAndUserId(matchId, userId), lockReason(match));
    }

    @Transactional
    public MyAvailabilityResponse setMine(Long matchId, Long userId, AvailabilityRequest request) {
        Match match = matchService.get(matchId);
        String lockReason = lockReason(match);
        if (lockReason != null) {
            throw ApiException.businessRule(lockReason);
        }
        User user = users.getReferenceById(userId);
        Availability availability = availabilities.findByMatchIdAndUserId(matchId, userId)
                .orElseGet(() -> new Availability(match, user));
        availability.update(request.status(), blankToNull(request.note()), user, false);
        availability = availabilities.saveAndFlush(availability);
        return toMine(match, Optional.of(availability), null);
    }

    /**
     * Correzione amministrativa: consentita anche a convocazione pubblicata (non per partite annullate),
     * registrata nell'audit log con il valore precedente.
     */
    @Transactional
    public PlayerAvailability adminSet(Long matchId, Long targetUserId, AvailabilityRequest request, Long actorId,
                                       String ip) {
        Match match = matchService.get(matchId);
        if (match.getStatus() == MatchStatus.CANCELLED) {
            throw ApiException.businessRule("La partita è annullata: le disponibilità non si possono modificare.");
        }
        User target = users.findById(targetUserId).filter(User::isActive)
                .orElseThrow(() -> ApiException.notFound("Utente non trovato o non attivo."));
        User actor = users.getReferenceById(actorId);
        Availability availability = availabilities.findByMatchIdAndUserId(matchId, targetUserId)
                .orElseGet(() -> new Availability(match, target));
        AvailabilityStatus before = availability.getId() == null ? AvailabilityStatus.PENDING : availability.getStatus();
        availability.update(request.status(), blankToNull(request.note()), actor, true);
        availability = availabilities.saveAndFlush(availability);
        auditService.record(actor, AuditAction.AVAILABILITY_OVERRIDDEN, "AVAILABILITY", availability.getId(),
                "Disponibilità di " + target.getFullName() + " corretta per la partita contro " + match.getOpponent(),
                Map.of("matchId", matchId, "userId", targetUserId, "before", before.name(),
                        "after", request.status().name()), ip);
        return toPlayer(target, availability);
    }

    /** Riepilogo per Allenatori e Admin: tutti gli utenti attivi, ordinati per cognome e nome. */
    @Transactional(readOnly = true)
    public TeamAvailabilityResponse team(Long matchId) {
        Match match = matchService.get(matchId);
        Map<Long, Availability> answers = availabilities.findByMatchId(matchId).stream()
                .collect(Collectors.toMap(a -> a.getUser().getId(), Function.identity()));
        List<PlayerAvailability> players = users.findByStatusOrderByLastNameAscFirstNameAsc(AccountStatus.ACTIVE)
                .stream()
                .map(user -> toPlayer(user, answers.get(user.getId())))
                .toList();
        Map<AvailabilityStatus, Long> counts = new EnumMap<>(AvailabilityStatus.class);
        for (AvailabilityStatus status : AvailabilityStatus.values()) {
            counts.put(status, players.stream().filter(p -> p.status() == status).count());
        }
        String lockReason = lockReason(match);
        return new TeamAvailabilityResponse(matchId, lockReason == null, lockReason, counts, players);
    }

    /** Prossime partite programmate con la disponibilità dell'utente (pagina "Disponibilità" e home). */
    @Transactional(readOnly = true)
    public List<UpcomingAvailability> upcomingForUser(Long userId, int limit) {
        List<Match> upcoming = matchService.upcoming(PageRequest.of(0, limit)).getContent();
        Map<Long, Availability> mine = availabilities
                .findByUserIdAndMatchIdIn(userId, upcoming.stream().map(Match::getId).toList()).stream()
                .collect(Collectors.toMap(a -> a.getMatch().getId(), Function.identity()));
        return upcoming.stream()
                .map(match -> new UpcomingAvailability(MatchResponse.from(match, matchService.zone()),
                        toMine(match, Optional.ofNullable(mine.get(match.getId())), lockReason(match))))
                .toList();
    }

    /** Motivo per cui un giocatore non può più modificare la propria disponibilità, oppure null. */
    String lockReason(Match match) {
        if (match.getStatus() == MatchStatus.CANCELLED) {
            return "La partita è stata annullata.";
        }
        if (match.getStatus() == MatchStatus.COMPLETED || !match.getKickoffAt().isAfter(clock.instant())) {
            return "La partita è già iniziata o conclusa.";
        }
        if (callups.existsByMatchIdAndStatus(match.getId(), CallupStatus.PUBLISHED)) {
            return "Le convocazioni sono state pubblicate: per modifiche contatta un amministratore.";
        }
        return null;
    }

    private MyAvailabilityResponse toMine(Match match, Optional<Availability> availability, String lockReason) {
        return availability
                .map(a -> new MyAvailabilityResponse(match.getId(), a.getStatus(), a.getNote(), a.isAdminOverride(),
                        toZoned(a.getUpdatedAt()), lockReason == null, lockReason))
                .orElseGet(() -> new MyAvailabilityResponse(match.getId(), AvailabilityStatus.PENDING, null, false,
                        null, lockReason == null, lockReason));
    }

    private PlayerAvailability toPlayer(User user, Availability availability) {
        if (availability == null) {
            return new PlayerAvailability(user.getId(), user.getFirstName(), user.getLastName(), user.getRole(),
                    AvailabilityStatus.PENDING, null, false, null);
        }
        return new PlayerAvailability(user.getId(), user.getFirstName(), user.getLastName(), user.getRole(),
                availability.getStatus(), availability.getNote(), availability.isAdminOverride(),
                toZoned(availability.getUpdatedAt()));
    }

    private OffsetDateTime toZoned(Instant instant) {
        ZoneId zone = matchService.zone();
        return instant == null ? null : instant.atZone(zone).toOffsetDateTime();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}

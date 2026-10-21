package it.squadra.gestionale.services;

import it.squadra.gestionale.dto.DashboardDto.DashboardResponse;
import it.squadra.gestionale.dto.DashboardDto.NextMatch;
import it.squadra.gestionale.dto.DashboardDto.PaymentSummary;
import it.squadra.gestionale.dto.DashboardDto.StaffTasks;
import it.squadra.gestionale.dto.MatchDto.MatchResponse;
import it.squadra.gestionale.dto.PaymentDto.MyPaymentsResponse;
import it.squadra.gestionale.entities.Availability;
import it.squadra.gestionale.entities.Availability.AvailabilityStatus;
import it.squadra.gestionale.entities.Callup.CallupStatus;
import it.squadra.gestionale.entities.EmailNotification.EmailStatus;
import it.squadra.gestionale.entities.Match;
import it.squadra.gestionale.entities.Match.MatchStatus;
import it.squadra.gestionale.entities.PaymentAssignment.PaymentAssignmentStatus;
import it.squadra.gestionale.entities.User.AccountStatus;
import it.squadra.gestionale.entities.User.Role;
import it.squadra.gestionale.repositories.AvailabilityRepository;
import it.squadra.gestionale.repositories.CallupPlayerRepository;
import it.squadra.gestionale.repositories.CallupRepository;
import it.squadra.gestionale.repositories.EmailNotificationRepository;
import it.squadra.gestionale.repositories.MatchRepository;
import it.squadra.gestionale.repositories.PaymentAssignmentRepository;
import it.squadra.gestionale.repositories.UserRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Dashboard della home: dati personali per tutti, cose da fare per lo staff. Solo letture, già consentite
 * singolarmente dalle altre API: i dati dello staff sono calcolati solo se il ruolo lo permette.
 */
@Service
public class DashboardService {

    /** Orizzonte entro cui una partita senza convocazione pubblicata è segnalata allo staff. */
    static final Duration CALLUP_HORIZON = Duration.ofDays(14);
    /** Una partita si considera finita (e da chiudere con il risultato) dopo questo tempo dal calcio d'inizio. */
    static final Duration RESULT_GRACE = Duration.ofHours(2);

    private final MatchService matchService;
    private final PaymentService paymentService;
    private final MatchRepository matches;
    private final AvailabilityRepository availabilities;
    private final CallupRepository callups;
    private final CallupPlayerRepository callupPlayers;
    private final EmailNotificationRepository emails;
    private final UserRepository users;
    private final PaymentAssignmentRepository assignments;
    private final Clock clock;

    public DashboardService(MatchService matchService, PaymentService paymentService, MatchRepository matches,
                            AvailabilityRepository availabilities, CallupRepository callups,
                            CallupPlayerRepository callupPlayers, EmailNotificationRepository emails,
                            UserRepository users, PaymentAssignmentRepository assignments, Clock clock) {
        this.matchService = matchService;
        this.paymentService = paymentService;
        this.matches = matches;
        this.availabilities = availabilities;
        this.callups = callups;
        this.callupPlayers = callupPlayers;
        this.emails = emails;
        this.users = users;
        this.assignments = assignments;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public DashboardResponse get(Long userId, Role role) {
        Instant now = clock.instant();
        MatchResponse lastResult = matches.findByStatusOrderByKickoffAtDesc(MatchStatus.COMPLETED, PageRequest.of(0, 1))
                .stream().findFirst().map(this::toResponse).orElse(null);
        MyPaymentsResponse mine = paymentService.mine(userId);
        boolean staff = role.ordinal() >= Role.COACH.ordinal();
        return new DashboardResponse(nextMatch(userId, now), lastResult,
                new PaymentSummary(mine.outstandingCents(), mine.overdueCount()),
                staff ? staffTasks(role == Role.ADMIN, now) : null);
    }

    private NextMatch nextMatch(Long userId, Instant now) {
        Match match = matchService.upcoming(PageRequest.of(0, 1)).stream().findFirst().orElse(null);
        if (match == null) {
            return null;
        }
        AvailabilityStatus availability = availabilities.findByMatchIdAndUserId(match.getId(), userId)
                .map(Availability::getStatus).orElse(AvailabilityStatus.PENDING);
        boolean published = callups.existsByMatchIdAndStatus(match.getId(), CallupStatus.PUBLISHED);
        boolean convocated = published && callupPlayers.findUpcomingPublishedForUser(userId, match.getKickoffAt())
                .stream().anyMatch(cp -> cp.getCallup().getMatch().getId().equals(match.getId()));
        return new NextMatch(toResponse(match), availability, match.getKickoffAt().isAfter(now) && !published,
                published, convocated);
    }

    private StaffTasks staffTasks(boolean admin, Instant now) {
        List<MatchResponse> withoutCallup = matches
                .findByStatusAndKickoffAtGreaterThanEqualAndKickoffAtLessThanOrderByKickoffAtAsc(MatchStatus.SCHEDULED,
                        now, now.plus(CALLUP_HORIZON))
                .stream()
                .filter(m -> !callups.existsByMatchIdAndStatus(m.getId(), CallupStatus.PUBLISHED))
                .map(this::toResponse)
                .toList();
        long failedEmails = emails.countByStatus(EmailStatus.FAILED);
        if (!admin) {
            return new StaffTasks(withoutCallup, failedEmails, null, null, null);
        }
        List<MatchResponse> resultsToRecord = matches
                .findByStatusAndKickoffAtLessThanOrderByKickoffAtDesc(MatchStatus.SCHEDULED, now.minus(RESULT_GRACE))
                .stream().map(this::toResponse).toList();
        return new StaffTasks(withoutCallup, failedEmails, resultsToRecord,
                users.countByStatusAndEmailVerifiedAtIsNotNull(AccountStatus.PENDING),
                assignments.sumAmountByStatus(PaymentAssignmentStatus.PENDING));
    }

    private MatchResponse toResponse(Match match) {
        return MatchResponse.from(match, matchService.zone());
    }
}

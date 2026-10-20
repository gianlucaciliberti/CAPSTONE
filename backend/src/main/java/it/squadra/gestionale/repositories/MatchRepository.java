package it.squadra.gestionale.repositories;

import it.squadra.gestionale.entities.Match;
import it.squadra.gestionale.entities.Match.MatchStatus;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MatchRepository extends JpaRepository<Match, Long> {

    /** Partite (di ogni stato) con calcio d'inizio nell'intervallo [from, to): usato per il calendario mensile. */
    List<Match> findByKickoffAtGreaterThanEqualAndKickoffAtLessThanOrderByKickoffAtAsc(Instant from, Instant to);

    Page<Match> findByStatusAndKickoffAtGreaterThanEqualOrderByKickoffAtAsc(MatchStatus status, Instant from,
                                                                             Pageable pageable);

    Page<Match> findByStatusOrderByKickoffAtDesc(MatchStatus status, Pageable pageable);

    List<Match> findByStatus(MatchStatus status);
}

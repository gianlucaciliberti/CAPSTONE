package it.squadra.gestionale.repositories;

import it.squadra.gestionale.entities.Match;
import it.squadra.gestionale.enums.MatchStatus;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MatchRepository extends JpaRepository<Match, Long> {

    List<Match> findByKickoffAtGreaterThanEqualAndKickoffAtLessThanOrderByKickoffAtAsc(Instant from, Instant to);

    Page<Match> findByStatusAndKickoffAtGreaterThanEqualOrderByKickoffAtAsc(MatchStatus status, Instant from,
                                                                             Pageable pageable);

    Page<Match> findByStatusOrderByKickoffAtDesc(MatchStatus status, Pageable pageable);
}

package it.squadra.gestionale.repositories;

import it.squadra.gestionale.entities.MatchEvent;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MatchEventRepository extends JpaRepository<MatchEvent, Long> {

    List<MatchEvent> findByMatchIdOrderByMinuteAscIdAsc(Long matchId);

    List<MatchEvent> findByPlayerId(Long playerId);
}

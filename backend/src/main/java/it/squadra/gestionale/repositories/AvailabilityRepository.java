package it.squadra.gestionale.repositories;

import it.squadra.gestionale.entities.Availability;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AvailabilityRepository extends JpaRepository<Availability, Long> {

    Optional<Availability> findByMatchIdAndUserId(Long matchId, Long userId);

    List<Availability> findByMatchId(Long matchId);

    List<Availability> findByUserIdAndMatchIdIn(Long userId, List<Long> matchIds);
}

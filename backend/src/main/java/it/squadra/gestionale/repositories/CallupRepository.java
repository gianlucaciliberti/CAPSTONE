package it.squadra.gestionale.repositories;

import it.squadra.gestionale.entities.Callup;
import it.squadra.gestionale.entities.Callup.CallupStatus;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CallupRepository extends JpaRepository<Callup, Long> {

    Optional<Callup> findByMatchId(Long matchId);

    boolean existsByMatchIdAndStatus(Long matchId, CallupStatus status);
}

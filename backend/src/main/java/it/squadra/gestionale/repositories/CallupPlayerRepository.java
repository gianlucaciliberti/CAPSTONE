package it.squadra.gestionale.repositories;

import it.squadra.gestionale.entities.CallupPlayer;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CallupPlayerRepository extends JpaRepository<CallupPlayer, Long> {

    List<CallupPlayer> findByCallupIdAndRemovedAtIsNull(Long callupId);

    List<CallupPlayer> findByUserIdAndRemovedAtIsNull(Long userId);
}

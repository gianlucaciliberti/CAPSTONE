package it.squadra.gestionale.repositories;

import it.squadra.gestionale.entities.Callup.CallupStatus;
import it.squadra.gestionale.entities.CallupPlayer;
import it.squadra.gestionale.entities.Match.MatchStatus;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CallupPlayerRepository extends JpaRepository<CallupPlayer, Long> {

    List<CallupPlayer> findByCallupIdAndRemovedAtIsNull(Long callupId);

    List<CallupPlayer> findByUserIdAndRemovedAtIsNull(Long userId);

    @Query("select cp from CallupPlayer cp join fetch cp.callup c join fetch c.match m "
            + "where cp.user.id = :userId and cp.removedAt is null and c.status = :callupStatus "
            + "and m.status = :matchStatus and m.kickoffAt >= :from order by m.kickoffAt")
    List<CallupPlayer> findActiveForUser(@Param("userId") Long userId, @Param("callupStatus") CallupStatus callupStatus,
                                         @Param("matchStatus") MatchStatus matchStatus, @Param("from") Instant from);

    /** Convocazioni pubblicate in cui l'utente è attualmente convocato, per partite programmate dal momento indicato. */
    default List<CallupPlayer> findUpcomingPublishedForUser(Long userId, Instant from) {
        return findActiveForUser(userId, CallupStatus.PUBLISHED, MatchStatus.SCHEDULED, from);
    }
}

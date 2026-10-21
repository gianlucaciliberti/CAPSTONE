package it.squadra.gestionale.repositories;

import it.squadra.gestionale.entities.Match.MatchStatus;
import it.squadra.gestionale.entities.MatchEvent;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MatchEventRepository extends JpaRepository<MatchEvent, Long> {

    List<MatchEvent> findByMatchIdOrderByMinuteAscIdAsc(Long matchId);

    /**
     * Eventi di tutte le partite in un certo stato, con il giocatore già caricato (statistiche). L'ordine per id
     * rende stabile il nome mostrato per i giocatori senza account (vale il primo inserito).
     */
    @EntityGraph(attributePaths = "player")
    List<MatchEvent> findByMatchStatusOrderByIdAsc(MatchStatus status);

    List<MatchEvent> findByPlayerId(Long playerId);
}

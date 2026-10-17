package it.squadra.gestionale.repositories;

import it.squadra.gestionale.entities.CallupRevision;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CallupRevisionRepository extends JpaRepository<CallupRevision, Long> {

    List<CallupRevision> findByCallupIdOrderByRevisionNumberDesc(Long callupId);
}

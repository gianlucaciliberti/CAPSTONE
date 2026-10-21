package it.squadra.gestionale.repositories;

import it.squadra.gestionale.entities.PaymentAssignment;
import it.squadra.gestionale.entities.PaymentAssignment.PaymentAssignmentStatus;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentAssignmentRepository extends JpaRepository<PaymentAssignment, Long> {

    List<PaymentAssignment> findByUserIdAndStatus(Long userId, PaymentAssignmentStatus status);

    Page<PaymentAssignment> findByUserId(Long userId, Pageable pageable);

    /** Quote dell'utente con la richiesta già caricata, dalla più recente. */
    @EntityGraph(attributePaths = "paymentRequest")
    List<PaymentAssignment> findByUserIdOrderByIdDesc(Long userId);

    List<PaymentAssignment> findByPaymentRequestId(Long paymentRequestId);

    /** Quote di più richieste in una sola query (riepiloghi dell'elenco). */
    List<PaymentAssignment> findByPaymentRequestIdIn(Collection<Long> paymentRequestIds);
}

package it.squadra.gestionale.repositories;

import it.squadra.gestionale.entities.PaymentAssignment;
import it.squadra.gestionale.entities.PaymentAssignment.PaymentAssignmentStatus;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentAssignmentRepository extends JpaRepository<PaymentAssignment, Long> {

    List<PaymentAssignment> findByUserIdAndStatus(Long userId, PaymentAssignmentStatus status);

    Page<PaymentAssignment> findByUserId(Long userId, Pageable pageable);

    List<PaymentAssignment> findByPaymentRequestId(Long paymentRequestId);
}

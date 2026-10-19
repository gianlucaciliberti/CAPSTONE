package it.squadra.gestionale.repositories;

import it.squadra.gestionale.entities.PaymentRequest;
import it.squadra.gestionale.entities.PaymentRequest.PaymentRequestStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRequestRepository extends JpaRepository<PaymentRequest, Long> {

    Page<PaymentRequest> findByStatus(PaymentRequestStatus status, Pageable pageable);
}

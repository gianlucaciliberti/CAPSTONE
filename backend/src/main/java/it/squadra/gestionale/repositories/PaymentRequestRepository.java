package it.squadra.gestionale.repositories;

import it.squadra.gestionale.entities.PaymentRequest;
import it.squadra.gestionale.entities.PaymentRequest.PaymentRequestStatus;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaymentRequestRepository extends JpaRepository<PaymentRequest, Long> {

    Page<PaymentRequest> findByStatus(PaymentRequestStatus status, Pageable pageable);

    /**
     * Richiesta con lock di riga: le modifiche alle quote della stessa richiesta vengono serializzate,
     * così lo stato aperta/chiusa resta coerente anche con due Admin che confermano insieme.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from PaymentRequest r where r.id = :id")
    Optional<PaymentRequest> lockById(@Param("id") Long id);
}

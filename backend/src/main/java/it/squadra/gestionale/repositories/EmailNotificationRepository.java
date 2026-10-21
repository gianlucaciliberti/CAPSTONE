package it.squadra.gestionale.repositories;

import it.squadra.gestionale.entities.EmailNotification;
import it.squadra.gestionale.entities.EmailNotification.EmailStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Le ricerche con filtri facoltativi usano le Specification (vedi EmailAdminService). */
public interface EmailNotificationRepository extends JpaRepository<EmailNotification, Long>,
        JpaSpecificationExecutor<EmailNotification> {

    boolean existsByIdempotencyKey(String idempotencyKey);

    Optional<EmailNotification> findByIdempotencyKey(String idempotencyKey);

    Optional<EmailNotification> findFirstByProviderMessageId(String providerMessageId);

    Page<EmailNotification> findByStatus(EmailStatus status, Pageable pageable);

    List<EmailNotification> findByStatus(EmailStatus status);

    long countByStatus(EmailStatus status);

    /**
     * Email da inviare ora, con lock di riga. SKIP LOCKED: righe già prese da un'altra istanza o da un'esecuzione
     * concorrente del worker vengono saltate, così la stessa email non viene inviata due volte in parallelo.
     */
    @Query(value = "select * from email_notifications where status = 'PENDING' and next_attempt_at <= :now "
            + "order by next_attempt_at, id limit :limit for update skip locked", nativeQuery = true)
    List<EmailNotification> lockDueForSending(@Param("now") Instant now, @Param("limit") int limit);
}

package it.squadra.gestionale.repositories;

import it.squadra.gestionale.entities.EmailNotification;
import it.squadra.gestionale.enums.EmailStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmailNotificationRepository extends JpaRepository<EmailNotification, Long> {

    boolean existsByIdempotencyKey(String idempotencyKey);

    Page<EmailNotification> findByStatus(EmailStatus status, Pageable pageable);

    Page<EmailNotification> findByRelatedEntityTypeAndRelatedEntityId(String relatedEntityType,
                                                                       String relatedEntityId, Pageable pageable);
}

package it.squadra.gestionale.dto;

import it.squadra.gestionale.entities.EmailNotification;
import it.squadra.gestionale.entities.EmailNotification.DeliveryStatus;
import it.squadra.gestionale.entities.EmailNotification.EmailStatus;
import it.squadra.gestionale.entities.EmailNotification.NotificationType;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Map;

/** DTO dello stato degli invii email (/api/v1/emails). Il contenuto delle email non viene mai esposto. */
public final class EmailDto {

    private EmailDto() {
    }

    /**
     * Invio email. {@code lastError} e {@code deliveryError} (messaggi tecnici del provider) sono valorizzati
     * solo per gli Admin.
     */
    public record EmailResponse(
            Long id,
            NotificationType type,
            String recipientName,
            String recipientEmail,
            String subject,
            EmailStatus status,
            int attempts,
            int maxAttempts,
            OffsetDateTime nextAttemptAt,
            OffsetDateTime sentAt,
            OffsetDateTime createdAt,
            String lastError,
            DeliveryStatus deliveryStatus,
            OffsetDateTime deliveryUpdatedAt,
            String deliveryError,
            String relatedEntityType,
            String relatedEntityId) {

        public static EmailResponse from(EmailNotification n, ZoneId zone, boolean admin) {
            return new EmailResponse(n.getId(), n.getType(), n.getRecipientName(), n.getRecipientEmail(),
                    n.getSubject(), n.getStatus(), n.getAttempts(), n.getMaxAttempts(), zoned(n.getNextAttemptAt(), zone),
                    zoned(n.getSentAt(), zone), zoned(n.getCreatedAt(), zone), admin ? n.getLastError() : null,
                    n.getDeliveryStatus(), zoned(n.getDeliveryUpdatedAt(), zone), admin ? n.getDeliveryError() : null,
                    n.getRelatedEntityType(), n.getRelatedEntityId());
        }

        private static OffsetDateTime zoned(java.time.Instant instant, ZoneId zone) {
            return instant == null ? null : instant.atZone(zone).toOffsetDateTime();
        }
    }

    /** Conteggio degli invii per stato (es. per una convocazione). */
    public record EmailSummary(long total, Map<EmailStatus, Long> byStatus) {
    }

    public record RetryResult(int retried) {
    }
}

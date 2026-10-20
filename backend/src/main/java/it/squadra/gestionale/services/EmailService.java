package it.squadra.gestionale.services;

import it.squadra.gestionale.config.AppProperties;
import it.squadra.gestionale.entities.EmailNotification;
import it.squadra.gestionale.entities.EmailNotification.NotificationType;
import it.squadra.gestionale.entities.User;
import it.squadra.gestionale.repositories.EmailNotificationRepository;
import it.squadra.gestionale.services.EmailSender.OutgoingEmail;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Coda persistente delle email (transactional outbox sulla tabella email_notifications).
 * <ul>
 *   <li>{@link #enqueue} va chiamato dentro la transazione dell'operazione che genera l'email: se l'operazione
 *       fallisce, l'email non viene accodata; se riesce, l'email non può andare persa.</li>
 *   <li>La chiave di idempotenza impedisce di accodare due volte la stessa email.</li>
 *   <li>Il worker invia le email in scadenza, con al massimo {@code app.mail.max-attempts} tentativi e attese
 *       crescenti tra un tentativo e l'altro; esauriti i tentativi l'email resta FAILED.</li>
 * </ul>
 * Garanzia "almeno una volta": se il processo si interrompe dopo l'invio ma prima del salvataggio,
 * l'email potrebbe essere inviata di nuovo al riavvio.
 */
@Service
public class EmailService {

    static final int BATCH_SIZE = 20;
    private static final Duration FIRST_RETRY_DELAY = Duration.ofSeconds(30);
    private static final Duration MAX_RETRY_DELAY = Duration.ofHours(1);
    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final EmailNotificationRepository repository;
    private final EmailSender sender;
    private final EmailTemplateService templates;
    private final JsonMapper jsonMapper;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;
    private final AppProperties.Mail mail;

    public EmailService(EmailNotificationRepository repository, EmailSender sender, EmailTemplateService templates,
                        JsonMapper jsonMapper,
                        PlatformTransactionManager transactionManager, Clock clock, AppProperties properties) {
        this.repository = repository;
        this.sender = sender;
        this.templates = templates;
        this.jsonMapper = jsonMapper;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.clock = clock;
        this.mail = properties.mail();
    }

    /**
     * Accoda un'email nella transazione corrente.
     *
     * @return false se un'email con la stessa chiave di idempotenza era già in coda (nessun duplicato creato)
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean enqueue(EmailMessage message) {
        if (repository.existsByIdempotencyKey(message.idempotencyKey())) {
            log.debug("Email già accodata, ignorata: {}", message.idempotencyKey());
            return false;
        }
        String payload = jsonMapper.writeValueAsString(Map.of("text", message.textBody(), "html", message.htmlBody()));
        User recipient = message.recipient();
        repository.save(new EmailNotification(message.idempotencyKey(), message.type(), recipient,
                recipient.getEmail(), recipient.getFullName(), message.subject(), payload,
                message.relatedEntityType(), message.relatedEntityId(), mail.maxAttempts(), clock.instant()));
        return true;
    }

    /**
     * Compone l'email con il template Thymeleaf indicato (variabili comuni: {@code firstName} e {@code subject})
     * e la accoda nella transazione corrente.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean enqueueTemplate(NotificationType type, User recipient, String subject, String template,
                                   Map<String, ?> variables, String relatedEntityType, String relatedEntityId,
                                   String idempotencyKey) {
        if (repository.existsByIdempotencyKey(idempotencyKey)) {
            return false;
        }
        Map<String, Object> all = new HashMap<>(variables);
        all.put("firstName", recipient.getFirstName());
        all.put("subject", subject);
        EmailBody body = templates.render(template, all);
        return enqueue(new EmailMessage(type, recipient, subject, body, relatedEntityType, relatedEntityId,
                idempotencyKey));
    }

    /** Esecuzione periodica del worker (disattivabile con MAIL_WORKER_ENABLED=false, es. nei test). */
    @Scheduled(fixedDelayString = "${app.mail.worker-interval:PT10S}", initialDelayString = "PT15S")
    public void scheduledProcessing() {
        if (mail.workerEnabled()) {
            processDueEmails();
        }
    }

    /**
     * Invia un lotto di email in scadenza. Ogni lotto è una transazione: le righe restano bloccate
     * (SELECT ... FOR UPDATE SKIP LOCKED) finché l'esito di ogni invio non è salvato.
     *
     * @return numero di email elaborate (inviate o fallite)
     */
    public int processDueEmails() {
        Integer processed = transactionTemplate.execute(status -> {
            Instant now = clock.instant();
            List<EmailNotification> due = repository.lockDueForSending(now, BATCH_SIZE);
            for (EmailNotification notification : due) {
                deliver(notification, now);
            }
            return due.size();
        });
        return processed == null ? 0 : processed;
    }

    private void deliver(EmailNotification notification, Instant now) {
        try {
            JsonNode payload = jsonMapper.readTree(notification.getPayload());
            String providerId = sender.send(new OutgoingEmail(notification.getRecipientEmail(),
                    notification.getRecipientName(), notification.getSubject(), payload.path("text").asString(),
                    payload.path("html").asString(), notification.getIdempotencyKey()));
            notification.markSent(providerId, now);
        } catch (Exception e) {
            notification.markAttemptFailed(e.getClass().getSimpleName() + ": " + e.getMessage(), now,
                    retryDelay(notification.getAttempts() + 1));
            log.warn("Invio email {} fallito (tentativo {}/{}): {}", notification.getId(),
                    notification.getAttempts(), notification.getMaxAttempts(), e.getMessage());
        }
    }

    /** Attesa esponenziale: 30s, 1m, 2m, 4m... fino a un massimo di 1 ora. */
    static Duration retryDelay(int attempt) {
        long factor = 1L << Math.min(attempt - 1, 20);
        Duration delay = FIRST_RETRY_DELAY.multipliedBy(factor);
        return delay.compareTo(MAX_RETRY_DELAY) > 0 ? MAX_RETRY_DELAY : delay;
    }

    /** Corpo dell'email in versione testuale e HTML. */
    public record EmailBody(String text, String html) {
    }

    /**
     * Email da accodare. Il contenuto è già composto (testo e HTML); la chiave di idempotenza identifica
     * in modo univoco l'evento e il destinatario (es. "PASSWORD_RESET:token:42").
     */
    public record EmailMessage(NotificationType type, User recipient, String subject, String textBody,
                               String htmlBody, String relatedEntityType, String relatedEntityId,
                               String idempotencyKey) {

        public EmailMessage(NotificationType type, User recipient, String subject, EmailBody body,
                            String relatedEntityType, String relatedEntityId, String idempotencyKey) {
            this(type, recipient, subject, body.text(), body.html(), relatedEntityType, relatedEntityId,
                    idempotencyKey);
        }
    }
}

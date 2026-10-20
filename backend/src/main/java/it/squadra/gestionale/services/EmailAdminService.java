package it.squadra.gestionale.services;

import it.squadra.gestionale.config.AppProperties;
import it.squadra.gestionale.dto.EmailDto.EmailResponse;
import it.squadra.gestionale.dto.EmailDto.EmailSummary;
import it.squadra.gestionale.entities.AuditLog.AuditAction;
import it.squadra.gestionale.entities.EmailNotification;
import it.squadra.gestionale.entities.EmailNotification.DeliveryStatus;
import it.squadra.gestionale.entities.EmailNotification.EmailStatus;
import it.squadra.gestionale.entities.EmailNotification.NotificationType;
import it.squadra.gestionale.entities.User;
import it.squadra.gestionale.exceptions.ApiException;
import it.squadra.gestionale.repositories.EmailNotificationRepository;
import it.squadra.gestionale.repositories.UserRepository;
import jakarta.persistence.criteria.Predicate;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Base64;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Stato degli invii email: consultazione (Allenatori e Admin), retry e annullamento manuali (Admin, con audit)
 * ed eventi di consegna ricevuti dal webhook Mailjet.
 */
@Service
public class EmailAdminService {

    private static final Logger log = LoggerFactory.getLogger(EmailAdminService.class);
    private static final Sort NEWEST_FIRST = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));

    private final EmailNotificationRepository repository;
    private final UserRepository users;
    private final AuditService auditService;
    private final JsonMapper jsonMapper;
    private final Clock clock;
    private final ZoneId zone;
    private final String webhookSecret;

    public EmailAdminService(EmailNotificationRepository repository, UserRepository users, AuditService auditService,
                             JsonMapper jsonMapper, Clock clock, AppProperties properties) {
        this.repository = repository;
        this.users = users;
        this.auditService = auditService;
        this.jsonMapper = jsonMapper;
        this.clock = clock;
        this.zone = properties.timezone();
        this.webhookSecret = properties.mail().mailjet().webhookSecret();
    }

    // --- Consultazione ---

    @Transactional(readOnly = true)
    public Page<EmailResponse> list(EmailStatus status, NotificationType type, String relatedType, String relatedId,
                                    Pageable pageable, boolean admin) {
        Pageable sorted = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), NEWEST_FIRST);
        return repository.findAll(filter(status, type, relatedType, relatedId), sorted)
                .map(n -> EmailResponse.from(n, zone, admin));
    }

    /** Conteggio per stato, es. di tutte le email di una convocazione. */
    @Transactional(readOnly = true)
    public EmailSummary summary(String relatedType, String relatedId) {
        Map<EmailStatus, Long> counts = new EnumMap<>(EmailStatus.class);
        for (EmailStatus status : EmailStatus.values()) {
            counts.put(status, repository.count(filter(status, null, relatedType, relatedId)));
        }
        return new EmailSummary(counts.values().stream().mapToLong(Long::longValue).sum(), counts);
    }

    // --- Gestione (Admin) ---

    /** Rimette in coda un'email FAILED con un nuovo ciclo di tentativi. */
    @Transactional
    public EmailResponse retry(Long id, Long actorId, String ip) {
        EmailNotification notification = find(id);
        if (notification.getStatus() != EmailStatus.FAILED) {
            throw ApiException.businessRule("Si possono ritentare solo gli invii falliti.");
        }
        audit(actorId, AuditAction.EMAIL_RETRIED, notification, "Nuovo tentativo di invio a "
                + notification.getRecipientEmail(), ip);
        notification.resetForRetry(clock.instant());
        return EmailResponse.from(notification, zone, true);
    }

    /** Rimette in coda tutte le email FAILED. */
    @Transactional
    public int retryAllFailed(Long actorId, String ip) {
        List<EmailNotification> failed = repository.findByStatus(EmailStatus.FAILED);
        Instant now = clock.instant();
        for (EmailNotification notification : failed) {
            audit(actorId, AuditAction.EMAIL_RETRIED, notification, "Nuovo tentativo di invio a "
                    + notification.getRecipientEmail() + " (ritento di tutti i falliti)", ip);
            notification.resetForRetry(now);
        }
        return failed.size();
    }

    /** Annulla un invio non ancora riuscito (in coda o fallito): non verrà più tentato. */
    @Transactional
    public EmailResponse cancel(Long id, Long actorId, String ip) {
        EmailNotification notification = find(id);
        if (notification.getStatus() != EmailStatus.PENDING && notification.getStatus() != EmailStatus.FAILED) {
            throw ApiException.businessRule("Si possono annullare solo gli invii in coda o falliti.");
        }
        notification.setStatus(EmailStatus.CANCELLED);
        audit(actorId, AuditAction.EMAIL_CANCELLED, notification, "Invio annullato a "
                + notification.getRecipientEmail(), ip);
        return EmailResponse.from(notification, zone, true);
    }

    // --- Webhook Mailjet ---

    /**
     * Elabora gli eventi di consegna. Il webhook è protetto con autenticazione Basic: la password configurata
     * in Mailjet (URL https://utente:password@host/...) deve coincidere con MAILJET_WEBHOOK_SECRET.
     * Senza segreto configurato il webhook è disattivato (404). Gli eventi sconosciuti vengono ignorati.
     *
     * @return numero di eventi associati a un'email
     */
    @Transactional
    public int handleMailjetWebhook(String authorizationHeader, String body) {
        if (webhookSecret == null || webhookSecret.isBlank()) {
            throw ApiException.notFound("Webhook non configurato.");
        }
        if (!validBasicPassword(authorizationHeader)) {
            throw ApiException.unauthenticated("Credenziali del webhook non valide.");
        }
        JsonNode root;
        try {
            root = jsonMapper.readTree(body);
        } catch (RuntimeException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ApiException.ErrorCode.VALIDATION_FAILED,
                    "Corpo del webhook non valido.");
        }
        // Con "Group events" attivo Mailjet invia un array, altrimenti un singolo oggetto.
        List<JsonNode> events = new ArrayList<>();
        if (root.isArray()) {
            root.forEach(events::add);
        } else {
            events.add(root);
        }
        int matched = 0;
        for (JsonNode event : events) {
            if (applyEvent(event)) {
                matched++;
            }
        }
        return matched;
    }

    private boolean applyEvent(JsonNode event) {
        Optional<DeliveryStatus> status = parseStatus(event.path("event").asString(""));
        if (status.isEmpty()) {
            return false;
        }
        String messageId = event.path("MessageID").asString("");
        String customId = event.path("CustomID").asString("");
        Optional<EmailNotification> notification = messageId.isBlank()
                ? Optional.empty()
                : repository.findFirstByProviderMessageId(messageId);
        if (notification.isEmpty() && !customId.isBlank()) {
            notification = repository.findByIdempotencyKey(customId);
        }
        if (notification.isEmpty()) {
            log.debug("Evento Mailjet {} per un messaggio sconosciuto ({}): ignorato", status.get(), messageId);
            return false;
        }
        long time = event.path("time").asLong(0);
        Instant eventTime = time > 0 ? Instant.ofEpochSecond(time) : clock.instant();
        String error = String.join(" ", event.path("error_related_to").asString(""),
                event.path("error").asString("")).trim();
        notification.get().recordDelivery(status.get(), eventTime, error.isEmpty() ? null : error);
        return true;
    }

    private static Optional<DeliveryStatus> parseStatus(String event) {
        try {
            return Optional.of(DeliveryStatus.valueOf(event.toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    /** Confronto a tempo costante della password Basic con il segreto configurato. */
    private boolean validBasicPassword(String header) {
        if (header == null || !header.regionMatches(true, 0, "Basic ", 0, 6)) {
            return false;
        }
        String decoded;
        try {
            decoded = new String(Base64.getDecoder().decode(header.substring(6).trim()), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return false;
        }
        int separator = decoded.indexOf(':');
        String password = separator < 0 ? "" : decoded.substring(separator + 1);
        return MessageDigest.isEqual(password.getBytes(StandardCharsets.UTF_8),
                webhookSecret.getBytes(StandardCharsets.UTF_8));
    }

    // --- Supporto ---

    private EmailNotification find(Long id) {
        return repository.findById(id).orElseThrow(() -> ApiException.notFound("Invio non trovato."));
    }

    private void audit(Long actorId, AuditAction action, EmailNotification notification, String summary, String ip) {
        User actor = users.getReferenceById(actorId);
        auditService.record(actor, action, "EMAIL", notification.getId(), summary,
                Map.of("type", notification.getType().name(), "attemptsBefore", notification.getAttempts()), ip);
    }

    private static Specification<EmailNotification> filter(EmailStatus status, NotificationType type,
                                                           String relatedType, String relatedId) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (type != null) {
                predicates.add(cb.equal(root.get("type"), type));
            }
            if (relatedType != null && !relatedType.isBlank()) {
                predicates.add(cb.equal(root.get("relatedEntityType"), relatedType));
            }
            if (relatedId != null && !relatedId.isBlank()) {
                predicates.add(cb.equal(root.get("relatedEntityId"), relatedId));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}

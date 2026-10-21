package it.squadra.gestionale.services;

import it.squadra.gestionale.config.AppProperties;
import it.squadra.gestionale.dto.PaymentDto.AssignmentResponse;
import it.squadra.gestionale.dto.PaymentDto.ConfirmRequest;
import it.squadra.gestionale.dto.PaymentDto.CreateRequest;
import it.squadra.gestionale.dto.PaymentDto.MyPayment;
import it.squadra.gestionale.dto.PaymentDto.MyPaymentsResponse;
import it.squadra.gestionale.dto.PaymentDto.PaymentRequestDetail;
import it.squadra.gestionale.dto.PaymentDto.PaymentRequestResponse;
import it.squadra.gestionale.dto.PaymentDto.UpdateRequest;
import it.squadra.gestionale.entities.AuditLog.AuditAction;
import it.squadra.gestionale.entities.PaymentAssignment;
import it.squadra.gestionale.entities.PaymentAssignment.PaymentAssignmentStatus;
import it.squadra.gestionale.entities.PaymentRequest;
import it.squadra.gestionale.entities.PaymentRequest.PaymentRequestStatus;
import it.squadra.gestionale.entities.User;
import it.squadra.gestionale.entities.User.AccountStatus;
import it.squadra.gestionale.exceptions.ApiException;
import it.squadra.gestionale.exceptions.ApiException.ErrorCode;
import it.squadra.gestionale.repositories.PaymentAssignmentRepository;
import it.squadra.gestionale.repositories.PaymentRequestRepository;
import it.squadra.gestionale.repositories.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Pagamenti manuali: l'Admin crea una richiesta (es. quota campo), la assegna agli utenti attivi e conferma a mano
 * i pagamenti ricevuti. Nessun dato viene cancellato: richieste e quote cambiano solo stato, ogni operazione è
 * registrata nell'audit log. La richiesta è aperta finché almeno una quota è da pagare, poi si chiude da sola.
 * Ogni utente vede solo le proprie quote.
 */
@Service
public class PaymentService {

    private final PaymentRequestRepository requests;
    private final PaymentAssignmentRepository assignments;
    private final UserRepository users;
    private final AuditService auditService;
    private final Clock clock;
    private final ZoneId zone;

    public PaymentService(PaymentRequestRepository requests, PaymentAssignmentRepository assignments,
                          UserRepository users, AuditService auditService, Clock clock, AppProperties properties) {
        this.requests = requests;
        this.assignments = assignments;
        this.users = users;
        this.auditService = auditService;
        this.clock = clock;
        this.zone = properties.timezone();
    }

    // --- Lettura ---

    /** Richieste dalla più recente, filtrate per stato se indicato, con i totali delle quote. */
    @Transactional(readOnly = true)
    public Page<PaymentRequestResponse> list(PaymentRequestStatus status, Pageable pageable) {
        Page<PaymentRequest> page = status == null ? requests.findAll(pageable) : requests.findByStatus(status, pageable);
        Map<Long, List<PaymentAssignment>> byRequest = assignments
                .findByPaymentRequestIdIn(page.map(PaymentRequest::getId).getContent()).stream()
                .collect(Collectors.groupingBy(a -> a.getPaymentRequest().getId()));
        return page.map(r -> toResponse(r, byRequest.getOrDefault(r.getId(), List.of())));
    }

    @Transactional(readOnly = true)
    public PaymentRequestDetail get(Long id) {
        return toDetail(find(id));
    }

    /** Quote dell'utente autenticato, dalla più recente, con il totale ancora da pagare. */
    @Transactional(readOnly = true)
    public MyPaymentsResponse mine(Long userId) {
        LocalDate today = today();
        List<MyPayment> payments = assignments.findByUserIdOrderByIdDesc(userId).stream()
                .map(a -> {
                    PaymentRequest r = a.getPaymentRequest();
                    boolean overdue = a.getStatus() == PaymentAssignmentStatus.PENDING && r.getDueDate() != null
                            && r.getDueDate().isBefore(today);
                    return new MyPayment(a.getId(), r.getId(), r.getDescription(), r.getNotes(), r.getDueDate(),
                            a.getAmountCents(), a.getStatus(), a.getPaidOn(), a.getPaymentMethod(), overdue);
                })
                .toList();
        int outstanding = payments.stream().filter(p -> p.status() == PaymentAssignmentStatus.PENDING)
                .mapToInt(MyPayment::amountCents).sum();
        int overdue = (int) payments.stream().filter(MyPayment::overdue).count();
        return new MyPaymentsResponse(outstanding, overdue, payments);
    }

    // --- Richieste ---

    @Transactional
    public PaymentRequestDetail create(CreateRequest request, Long actorId, String ip) {
        User actor = users.getReferenceById(actorId);
        PaymentRequest payment = new PaymentRequest(request.description().trim(), request.amountCents(),
                request.dueDate(), blankToNull(request.notes()), actor);
        List<User> recipients = activeUsers(request.userIds());
        recipients.forEach(payment::assignTo);
        payment = requests.saveAndFlush(payment);
        Map<String, Object> details = new LinkedHashMap<>(snapshot(payment));
        details.put("recipients", recipients.stream().map(User::getFullName).toList());
        auditService.record(actor, AuditAction.PAYMENT_REQUEST_CREATED, "PAYMENT_REQUEST", payment.getId(),
                "Richiesta di pagamento \"" + payment.getDescription() + "\" per " + recipients.size() + " utenti",
                details, ip);
        return toDetail(payment);
    }

    @Transactional
    public PaymentRequestDetail update(Long id, UpdateRequest request, Long actorId, String ip) {
        PaymentRequest payment = lock(id);
        ensureVersion(payment.getVersion(), request.version(), "La richiesta");
        ensureNotCancelled(payment);
        Map<String, Object> before = snapshot(payment);
        User actor = users.getReferenceById(actorId);
        payment.updateDetails(request.description().trim(), request.dueDate(), blankToNull(request.notes()), actor);
        Map<String, Object> after = snapshot(payment);
        if (!before.equals(after)) {
            auditService.record(actor, AuditAction.PAYMENT_REQUEST_UPDATED, "PAYMENT_REQUEST", id,
                    "Richiesta di pagamento \"" + payment.getDescription() + "\" modificata",
                    Map.of("before", before, "after", after), ip);
        }
        requests.flush();
        return toDetail(payment);
    }

    /**
     * Annulla una richiesta aperta: le quote ancora da pagare vengono annullate, quelle già pagate restano tali
     * (un eventuale rimborso si gestisce a mano).
     */
    @Transactional
    public PaymentRequestDetail cancel(Long id, String reason, Long version, Long actorId, String ip) {
        PaymentRequest payment = lock(id);
        ensureVersion(payment.getVersion(), version, "La richiesta");
        if (payment.getStatus() != PaymentRequestStatus.OPEN) {
            throw ApiException.businessRule("Si possono annullare solo le richieste aperte.");
        }
        User actor = users.getReferenceById(actorId);
        Instant now = clock.instant();
        int cancelled = 0;
        for (PaymentAssignment a : payment.getAssignments()) {
            if (a.getStatus() == PaymentAssignmentStatus.PENDING) {
                a.cancel(actor, now);
                cancelled++;
            }
        }
        payment.cancel(blankToNull(reason), actor, now);
        Map<String, Object> details = new LinkedHashMap<>(snapshot(payment));
        details.put("reason", reason == null ? "" : reason);
        details.put("cancelledAssignments", cancelled);
        auditService.record(actor, AuditAction.PAYMENT_REQUEST_CANCELLED, "PAYMENT_REQUEST", id,
                "Richiesta di pagamento \"" + payment.getDescription() + "\" annullata", details, ip);
        requests.flush();
        return toDetail(payment);
    }

    /** Aggiunge destinatari a una richiesta non annullata (es. un nuovo giocatore); una richiesta chiusa si riapre. */
    @Transactional
    public PaymentRequestDetail assign(Long id, List<Long> userIds, Long actorId, String ip) {
        PaymentRequest payment = lock(id);
        ensureNotCancelled(payment);
        List<User> recipients = activeUsers(userIds);
        for (User user : recipients) {
            boolean already = payment.getAssignments().stream().anyMatch(a -> a.getUser().getId().equals(user.getId()));
            if (already) {
                throw ApiException.businessRule(user.getFullName() + " è già tra i destinatari.");
            }
        }
        User actor = users.getReferenceById(actorId);
        recipients.forEach(payment::assignTo);
        payment.refreshStatus(actor);
        auditService.record(actor, AuditAction.PAYMENT_ASSIGNED, "PAYMENT_REQUEST", id,
                "Aggiunti " + recipients.size() + " destinatari a \"" + payment.getDescription() + "\"",
                Map.of("recipients", recipients.stream().map(User::getFullName).toList()), ip);
        requests.flush();
        return toDetail(payment);
    }

    // --- Quote ---

    @Transactional
    public PaymentRequestDetail confirm(Long requestId, Long assignmentId, ConfirmRequest request, Long actorId,
                                        String ip) {
        PaymentRequest payment = lock(requestId);
        ensureNotCancelled(payment);
        PaymentAssignment assignment = findAssignment(payment, assignmentId, request.version());
        if (assignment.getStatus() != PaymentAssignmentStatus.PENDING) {
            throw ApiException.businessRule("Si possono confermare solo le quote da pagare.");
        }
        if (request.paidOn().isAfter(today())) {
            throw ApiException.businessRule("La data del pagamento non può essere nel futuro.");
        }
        User actor = users.getReferenceById(actorId);
        assignment.confirmPayment(request.paidOn(), request.method(), blankToNull(request.note()), actor,
                clock.instant());
        payment.refreshStatus(actor);
        auditService.record(actor, AuditAction.PAYMENT_CONFIRMED, "PAYMENT_ASSIGNMENT", assignmentId,
                "Pagamento di " + assignment.getUser().getFullName() + " confermato per \"" + payment.getDescription()
                        + "\"", Map.of("requestId", requestId, "after", describe(assignment)), ip);
        requests.flush();
        return toDetail(payment);
    }

    /** Esonera un utente: la quota non è più dovuta. */
    @Transactional
    public PaymentRequestDetail waive(Long requestId, Long assignmentId, Long version, Long actorId, String ip) {
        PaymentRequest payment = lock(requestId);
        ensureNotCancelled(payment);
        PaymentAssignment assignment = findAssignment(payment, assignmentId, version);
        if (assignment.getStatus() != PaymentAssignmentStatus.PENDING) {
            throw ApiException.businessRule("Si possono esonerare solo le quote da pagare.");
        }
        User actor = users.getReferenceById(actorId);
        assignment.cancel(actor, clock.instant());
        payment.refreshStatus(actor);
        auditService.record(actor, AuditAction.PAYMENT_WAIVED, "PAYMENT_ASSIGNMENT", assignmentId,
                assignment.getUser().getFullName() + " esonerato da \"" + payment.getDescription() + "\"",
                Map.of("requestId", requestId), ip);
        requests.flush();
        return toDetail(payment);
    }

    /** Riporta una quota pagata o esonerata a "da pagare" (correzione di un errore); i dati precedenti vanno nell'audit. */
    @Transactional
    public PaymentRequestDetail reopen(Long requestId, Long assignmentId, Long version, Long actorId, String ip) {
        PaymentRequest payment = lock(requestId);
        ensureNotCancelled(payment);
        PaymentAssignment assignment = findAssignment(payment, assignmentId, version);
        if (assignment.getStatus() == PaymentAssignmentStatus.PENDING) {
            throw ApiException.businessRule("La quota è già da pagare.");
        }
        Map<String, Object> before = describe(assignment);
        User actor = users.getReferenceById(actorId);
        assignment.reopen();
        payment.refreshStatus(actor);
        auditService.record(actor, AuditAction.PAYMENT_REOPENED, "PAYMENT_ASSIGNMENT", assignmentId,
                "Quota di " + assignment.getUser().getFullName() + " riaperta per \"" + payment.getDescription() + "\"",
                Map.of("requestId", requestId, "before", before), ip);
        requests.flush();
        return toDetail(payment);
    }

    // --- Regole ---

    private PaymentRequest find(Long id) {
        return requests.findById(id).orElseThrow(() -> ApiException.notFound("Richiesta di pagamento non trovata."));
    }

    private PaymentRequest lock(Long id) {
        return requests.lockById(id).orElseThrow(() -> ApiException.notFound("Richiesta di pagamento non trovata."));
    }

    private PaymentAssignment findAssignment(PaymentRequest payment, Long assignmentId, Long version) {
        PaymentAssignment assignment = payment.getAssignments().stream()
                .filter(a -> a.getId().equals(assignmentId))
                .findFirst()
                .orElseThrow(() -> ApiException.notFound("Quota non trovata."));
        ensureVersion(assignment.getVersion(), version, "La quota");
        return assignment;
    }

    /** Utenti attivi indicati, senza duplicati; un id inesistente o non attivo blocca l'operazione. */
    private List<User> activeUsers(List<Long> ids) {
        List<Long> unique = List.copyOf(new LinkedHashSet<>(ids));
        Map<Long, User> found = users.findAllById(unique).stream()
                .collect(Collectors.toMap(User::getId, u -> u));
        return unique.stream().map(id -> {
            User user = found.get(id);
            if (user == null || user.getStatus() != AccountStatus.ACTIVE) {
                throw ApiException.businessRule("Si possono indicare solo utenti attivi.");
            }
            return user;
        }).toList();
    }

    private static void ensureNotCancelled(PaymentRequest payment) {
        if (payment.getStatus() == PaymentRequestStatus.CANCELLED) {
            throw ApiException.businessRule("La richiesta è stata annullata e non può essere modificata.");
        }
    }

    private static void ensureVersion(long current, Long expected, String subject) {
        if (!Objects.equals(expected, current)) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.CONCURRENT_MODIFICATION,
                    subject + " è stata modificata da qualcun altro. Ricarica la pagina e riprova.");
        }
    }

    private LocalDate today() {
        return LocalDate.now(clock.withZone(zone));
    }

    // --- Risposte ---

    private PaymentRequestDetail toDetail(PaymentRequest payment) {
        List<PaymentAssignment> list = payment.getAssignments();
        List<AssignmentResponse> rows = list.stream()
                .sorted(Comparator.comparing((PaymentAssignment a) -> a.getUser().getLastName(),
                                String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(a -> a.getUser().getFirstName(), String.CASE_INSENSITIVE_ORDER))
                .map(a -> new AssignmentResponse(a.getId(), a.getUser().getId(), a.getUser().getFullName(),
                        a.getAmountCents(), a.getStatus(), a.getPaidOn(), a.getPaymentMethod(), a.getAdminNote(),
                        toOffset(a.getConfirmedAt()),
                        a.getConfirmedBy() == null ? null : a.getConfirmedBy().getFullName(), a.getVersion()))
                .toList();
        return new PaymentRequestDetail(toResponse(payment, list), rows);
    }

    private PaymentRequestResponse toResponse(PaymentRequest r, List<PaymentAssignment> list) {
        int pending = 0;
        int paid = 0;
        int cancelled = 0;
        int collected = 0;
        int outstanding = 0;
        for (PaymentAssignment a : list) {
            switch (a.getStatus()) {
                case PENDING -> {
                    pending++;
                    outstanding += a.getAmountCents();
                }
                case PAID -> {
                    paid++;
                    collected += a.getAmountCents();
                }
                case CANCELLED -> cancelled++;
            }
        }
        return new PaymentRequestResponse(r.getId(), r.getDescription(), r.getAmountCents(), r.getCurrency(),
                r.getDueDate(), r.getNotes(), r.getStatus(), toOffset(r.getCreatedAt()), r.getCancellationReason(),
                pending, paid, cancelled, collected, outstanding, r.getVersion());
    }

    private OffsetDateTime toOffset(Instant instant) {
        return instant == null ? null : instant.atZone(zone).toOffsetDateTime();
    }

    private static Map<String, Object> snapshot(PaymentRequest r) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("description", r.getDescription());
        data.put("amountCents", r.getAmountCents());
        data.put("dueDate", r.getDueDate() == null ? "" : r.getDueDate().toString());
        data.put("notes", r.getNotes() == null ? "" : r.getNotes());
        data.put("status", r.getStatus().name());
        return data;
    }

    private static Map<String, Object> describe(PaymentAssignment a) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("user", a.getUser().getFullName());
        data.put("amountCents", a.getAmountCents());
        data.put("status", a.getStatus().name());
        data.put("paidOn", a.getPaidOn() == null ? "" : a.getPaidOn().toString());
        data.put("method", a.getPaymentMethod() == null ? "" : a.getPaymentMethod().name());
        data.put("note", a.getAdminNote() == null ? "" : a.getAdminNote());
        return data;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}

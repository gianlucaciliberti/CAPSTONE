package it.squadra.gestionale.controllers;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import it.squadra.gestionale.dto.CommonDto.MessageResponse;
import it.squadra.gestionale.dto.CommonDto.PageResponse;
import it.squadra.gestionale.dto.EmailDto.EmailResponse;
import it.squadra.gestionale.dto.EmailDto.EmailSummary;
import it.squadra.gestionale.dto.EmailDto.RetryResult;
import it.squadra.gestionale.entities.EmailNotification.EmailStatus;
import it.squadra.gestionale.entities.EmailNotification.NotificationType;
import it.squadra.gestionale.entities.User.Role;
import it.squadra.gestionale.security.UserJwtAuthenticationConverter.AuthenticatedUser;
import it.squadra.gestionale.services.EmailAdminService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpHeaders;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Stato degli invii email (Allenatori e Admin), gestione degli invii falliti (Admin) e webhook Mailjet. */
@Tag(name = "Email")
@RestController
public class EmailController {

    private final EmailAdminService emailAdminService;

    public EmailController(EmailAdminService emailAdminService) {
        this.emailAdminService = emailAdminService;
    }

    @Operation(summary = "Elenco degli invii, filtrabile per stato, tipo ed entità collegata (es. CALLUP + id)")
    @GetMapping("/api/v1/emails")
    @PreAuthorize("hasRole('COACH')")
    public PageResponse<EmailResponse> list(@RequestParam(required = false) EmailStatus status,
                                            @RequestParam(required = false) NotificationType type,
                                            @RequestParam(required = false) String relatedType,
                                            @RequestParam(required = false) String relatedId,
                                            @PageableDefault(size = 20) Pageable pageable,
                                            @AuthenticationPrincipal AuthenticatedUser user) {
        return PageResponse.of(emailAdminService.list(status, type, relatedType, relatedId, pageable,
                user.role() == Role.ADMIN), e -> e);
    }

    @Operation(summary = "Conteggio degli invii per stato (es. di una convocazione)")
    @GetMapping("/api/v1/emails/summary")
    @PreAuthorize("hasRole('COACH')")
    public EmailSummary summary(@RequestParam(required = false) String relatedType,
                                @RequestParam(required = false) String relatedId) {
        return emailAdminService.summary(relatedType, relatedId);
    }

    @Operation(summary = "Ritenta un invio fallito (Admin)")
    @PostMapping("/api/v1/emails/{id}/retry")
    @PreAuthorize("hasRole('ADMIN')")
    public EmailResponse retry(@PathVariable Long id, @AuthenticationPrincipal AuthenticatedUser user,
                               HttpServletRequest http) {
        return emailAdminService.retry(id, user.id(), http.getRemoteAddr());
    }

    @Operation(summary = "Ritenta tutti gli invii falliti (Admin)")
    @PostMapping("/api/v1/emails/retry-failed")
    @PreAuthorize("hasRole('ADMIN')")
    public RetryResult retryAllFailed(@AuthenticationPrincipal AuthenticatedUser user, HttpServletRequest http) {
        return new RetryResult(emailAdminService.retryAllFailed(user.id(), http.getRemoteAddr()));
    }

    @Operation(summary = "Annulla un invio in coda o fallito (Admin)")
    @PostMapping("/api/v1/emails/{id}/cancel")
    @PreAuthorize("hasRole('ADMIN')")
    public EmailResponse cancel(@PathVariable Long id, @AuthenticationPrincipal AuthenticatedUser user,
                                HttpServletRequest http) {
        return emailAdminService.cancel(id, user.id(), http.getRemoteAddr());
    }

    /**
     * Eventi di consegna di Mailjet. Pubblico per Spring Security, ma protetto da autenticazione Basic
     * verificata nel servizio (password = MAILJET_WEBHOOK_SECRET). Risponde 200 anche per eventi non riconosciuti,
     * così Mailjet non li reinvia.
     */
    @Operation(summary = "Webhook eventi Mailjet (autenticazione Basic con MAILJET_WEBHOOK_SECRET)")
    @PostMapping("/api/v1/webhooks/mailjet")
    public MessageResponse mailjetWebhook(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false)
                                          String authorization, @RequestBody String body) {
        int matched = emailAdminService.handleMailjetWebhook(authorization, body);
        return new MessageResponse("Eventi elaborati: " + matched);
    }
}

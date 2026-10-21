package it.squadra.gestionale.controllers;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import it.squadra.gestionale.dto.CommonDto.PageResponse;
import it.squadra.gestionale.dto.PaymentDto.AssignRequest;
import it.squadra.gestionale.dto.PaymentDto.CancelRequest;
import it.squadra.gestionale.dto.PaymentDto.ConfirmRequest;
import it.squadra.gestionale.dto.PaymentDto.CreateRequest;
import it.squadra.gestionale.dto.PaymentDto.MyPaymentsResponse;
import it.squadra.gestionale.dto.PaymentDto.PaymentRequestDetail;
import it.squadra.gestionale.dto.PaymentDto.PaymentRequestResponse;
import it.squadra.gestionale.dto.PaymentDto.UpdateRequest;
import it.squadra.gestionale.dto.PaymentDto.VersionRequest;
import it.squadra.gestionale.entities.PaymentRequest.PaymentRequestStatus;
import it.squadra.gestionale.security.UserJwtAuthenticationConverter.AuthenticatedUser;
import it.squadra.gestionale.services.PaymentService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Pagamenti manuali: ogni utente vede solo le proprie quote ("/me"), tutto il resto è riservato all'Admin.
 * Le modifiche richiedono la versione letta in precedenza (409 in caso di modifiche concorrenti).
 */
@Tag(name = "Pagamenti")
@RestController
@RequestMapping("/api/v1/payments")
@PreAuthorize("hasRole('ADMIN')")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @Operation(summary = "Le mie quote, con il totale da pagare")
    @GetMapping("/me")
    @PreAuthorize("hasRole('PLAYER')")
    public MyPaymentsResponse mine(@AuthenticationPrincipal AuthenticatedUser user) {
        return paymentService.mine(user.id());
    }

    @Operation(summary = "Richieste di pagamento, dalla più recente, con i totali (Admin)")
    @GetMapping
    public PageResponse<PaymentRequestResponse> list(
            @RequestParam(required = false) PaymentRequestStatus status,
            @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
        return PageResponse.of(paymentService.list(status, pageable), r -> r);
    }

    @Operation(summary = "Dettaglio di una richiesta con le quote (Admin)")
    @GetMapping("/{id}")
    public PaymentRequestDetail get(@PathVariable Long id) {
        return paymentService.get(id);
    }

    @Operation(summary = "Crea una richiesta e la assegna agli utenti indicati (Admin)")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PaymentRequestDetail create(@Valid @RequestBody CreateRequest request,
                                       @AuthenticationPrincipal AuthenticatedUser user, HttpServletRequest http) {
        return paymentService.create(request, user.id(), http.getRemoteAddr());
    }

    @Operation(summary = "Modifica descrizione, scadenza e note (Admin); l'importo non è modificabile")
    @PutMapping("/{id}")
    public PaymentRequestDetail update(@PathVariable Long id, @Valid @RequestBody UpdateRequest request,
                                       @AuthenticationPrincipal AuthenticatedUser user, HttpServletRequest http) {
        return paymentService.update(id, request, user.id(), http.getRemoteAddr());
    }

    @Operation(summary = "Annulla una richiesta aperta (Admin); le quote pagate restano registrate")
    @PostMapping("/{id}/cancel")
    public PaymentRequestDetail cancel(@PathVariable Long id, @Valid @RequestBody CancelRequest request,
                                       @AuthenticationPrincipal AuthenticatedUser user, HttpServletRequest http) {
        return paymentService.cancel(id, request.reason(), request.version(), user.id(), http.getRemoteAddr());
    }

    @Operation(summary = "Aggiunge destinatari a una richiesta (Admin)")
    @PostMapping("/{id}/assignments")
    public PaymentRequestDetail assign(@PathVariable Long id, @Valid @RequestBody AssignRequest request,
                                       @AuthenticationPrincipal AuthenticatedUser user, HttpServletRequest http) {
        return paymentService.assign(id, request.userIds(), user.id(), http.getRemoteAddr());
    }

    @Operation(summary = "Conferma il pagamento di una quota (Admin)")
    @PostMapping("/{id}/assignments/{assignmentId}/confirm")
    public PaymentRequestDetail confirm(@PathVariable Long id, @PathVariable Long assignmentId,
                                        @Valid @RequestBody ConfirmRequest request,
                                        @AuthenticationPrincipal AuthenticatedUser user, HttpServletRequest http) {
        return paymentService.confirm(id, assignmentId, request, user.id(), http.getRemoteAddr());
    }

    @Operation(summary = "Esonera un utente dalla quota (Admin)")
    @PostMapping("/{id}/assignments/{assignmentId}/waive")
    public PaymentRequestDetail waive(@PathVariable Long id, @PathVariable Long assignmentId,
                                      @Valid @RequestBody VersionRequest request,
                                      @AuthenticationPrincipal AuthenticatedUser user, HttpServletRequest http) {
        return paymentService.waive(id, assignmentId, request.version(), user.id(), http.getRemoteAddr());
    }

    @Operation(summary = "Riporta a \"da pagare\" una quota pagata o esonerata per errore (Admin)")
    @PostMapping("/{id}/assignments/{assignmentId}/reopen")
    public PaymentRequestDetail reopen(@PathVariable Long id, @PathVariable Long assignmentId,
                                       @Valid @RequestBody VersionRequest request,
                                       @AuthenticationPrincipal AuthenticatedUser user, HttpServletRequest http) {
        return paymentService.reopen(id, assignmentId, request.version(), user.id(), http.getRemoteAddr());
    }
}

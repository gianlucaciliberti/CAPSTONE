package it.squadra.gestionale.controllers;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import it.squadra.gestionale.dto.CallupDto.CallupRequest;
import it.squadra.gestionale.dto.CallupDto.CallupResponse;
import it.squadra.gestionale.dto.CallupDto.MyCallupResponse;
import it.squadra.gestionale.dto.CallupDto.PublishRequest;
import it.squadra.gestionale.dto.CallupDto.RevisionResponse;
import it.squadra.gestionale.dto.CallupDto.WithdrawRequest;
import it.squadra.gestionale.entities.User.Role;
import it.squadra.gestionale.security.UserJwtAuthenticationConverter.AuthenticatedUser;
import it.squadra.gestionale.services.CallupService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Convocazioni: lettura per tutti (le bozze solo per lo staff), gestione per Allenatori e Admin. */
@Tag(name = "Convocazioni")
@RestController
@PreAuthorize("hasRole('PLAYER')")
public class CallupController {

    private final CallupService callupService;

    public CallupController(CallupService callupService) {
        this.callupService = callupService;
    }

    @Operation(summary = "Convocazione della partita (i giocatori vedono solo quelle pubblicate o ritirate)")
    @GetMapping("/api/v1/matches/{matchId}/callup")
    public CallupResponse get(@PathVariable Long matchId, @AuthenticationPrincipal AuthenticatedUser user) {
        return callupService.view(matchId, user.id(), isStaff(user));
    }

    @Operation(summary = "Salva la convocazione: bozza, oppure modifica tracciata se già pubblicata (Allenatore)")
    @PutMapping("/api/v1/matches/{matchId}/callup")
    @PreAuthorize("hasRole('COACH')")
    public CallupResponse save(@PathVariable Long matchId, @Valid @RequestBody CallupRequest request,
                               @AuthenticationPrincipal AuthenticatedUser user, HttpServletRequest http) {
        return callupService.save(matchId, request, user.id(), http.getRemoteAddr());
    }

    @Operation(summary = "Pubblica la convocazione in bozza (Allenatore)")
    @PostMapping("/api/v1/matches/{matchId}/callup/publish")
    @PreAuthorize("hasRole('COACH')")
    public CallupResponse publish(@PathVariable Long matchId, @Valid @RequestBody PublishRequest request,
                                  @AuthenticationPrincipal AuthenticatedUser user, HttpServletRequest http) {
        return callupService.publish(matchId, request.version(), request.confirmed(), user.id(),
                http.getRemoteAddr());
    }

    @Operation(summary = "Ritira una convocazione pubblicata (Allenatore); resta nello storico")
    @PostMapping("/api/v1/matches/{matchId}/callup/withdraw")
    @PreAuthorize("hasRole('COACH')")
    public CallupResponse withdraw(@PathVariable Long matchId, @Valid @RequestBody WithdrawRequest request,
                                   @AuthenticationPrincipal AuthenticatedUser user, HttpServletRequest http) {
        return callupService.withdraw(matchId, request.version(), request.reason(), user.id(), http.getRemoteAddr());
    }

    @Operation(summary = "Storico delle revisioni della convocazione (Allenatore)")
    @GetMapping("/api/v1/matches/{matchId}/callup/revisions")
    @PreAuthorize("hasRole('COACH')")
    public List<RevisionResponse> revisions(@PathVariable Long matchId) {
        return callupService.revisions(matchId);
    }

    @Operation(summary = "Le mie convocazioni pubblicate per le prossime partite")
    @GetMapping("/api/v1/callups/me")
    public List<MyCallupResponse> mine(@AuthenticationPrincipal AuthenticatedUser user) {
        return callupService.mine(user.id());
    }

    private static boolean isStaff(AuthenticatedUser user) {
        return user.role() == Role.COACH || user.role() == Role.ADMIN;
    }
}

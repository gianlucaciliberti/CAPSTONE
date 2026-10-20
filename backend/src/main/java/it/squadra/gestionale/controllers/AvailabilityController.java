package it.squadra.gestionale.controllers;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import it.squadra.gestionale.dto.AvailabilityDto.AvailabilityRequest;
import it.squadra.gestionale.dto.AvailabilityDto.MyAvailabilityResponse;
import it.squadra.gestionale.dto.AvailabilityDto.PlayerAvailability;
import it.squadra.gestionale.dto.AvailabilityDto.TeamAvailabilityResponse;
import it.squadra.gestionale.dto.AvailabilityDto.UpcomingAvailability;
import it.squadra.gestionale.security.UserJwtAuthenticationConverter.AuthenticatedUser;
import it.squadra.gestionale.services.AvailabilityService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Disponibilità. "me" = sempre e solo l'utente autenticato: non esiste un parametro per agire su altri utenti,
 * tranne l'endpoint di correzione riservato all'Admin.
 */
@Tag(name = "Disponibilità")
@RestController
@PreAuthorize("hasRole('PLAYER')")
public class AvailabilityController {

    private final AvailabilityService availabilityService;

    public AvailabilityController(AvailabilityService availabilityService) {
        this.availabilityService = availabilityService;
    }

    @Operation(summary = "La mia disponibilità per una partita")
    @GetMapping("/api/v1/matches/{matchId}/availability/me")
    public MyAvailabilityResponse mine(@PathVariable Long matchId, @AuthenticationPrincipal AuthenticatedUser user) {
        return availabilityService.mine(matchId, user.id());
    }

    @Operation(summary = "Imposta la mia disponibilità (fino alla pubblicazione delle convocazioni)")
    @PutMapping("/api/v1/matches/{matchId}/availability/me")
    public MyAvailabilityResponse setMine(@PathVariable Long matchId, @Valid @RequestBody AvailabilityRequest request,
                                          @AuthenticationPrincipal AuthenticatedUser user) {
        return availabilityService.setMine(matchId, user.id(), request);
    }

    @Operation(summary = "Riepilogo delle disponibilità della squadra (Allenatore e Admin)")
    @GetMapping("/api/v1/matches/{matchId}/availability")
    @PreAuthorize("hasRole('COACH')")
    public TeamAvailabilityResponse team(@PathVariable Long matchId) {
        return availabilityService.team(matchId);
    }

    @Operation(summary = "Correzione amministrativa della disponibilità di un utente (Admin)")
    @PutMapping("/api/v1/matches/{matchId}/availability/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    public PlayerAvailability adminSet(@PathVariable Long matchId, @PathVariable Long userId,
                                       @Valid @RequestBody AvailabilityRequest request,
                                       @AuthenticationPrincipal AuthenticatedUser actor, HttpServletRequest http) {
        return availabilityService.adminSet(matchId, userId, request, actor.id(), http.getRemoteAddr());
    }

    @Operation(summary = "Le prossime partite con la mia disponibilità")
    @GetMapping("/api/v1/availability/me/upcoming")
    public List<UpcomingAvailability> upcoming(@RequestParam(defaultValue = "10") @Min(1) @Max(50) int limit,
                                               @AuthenticationPrincipal AuthenticatedUser user) {
        return availabilityService.upcomingForUser(user.id(), limit);
    }
}

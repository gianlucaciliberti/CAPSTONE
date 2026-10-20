package it.squadra.gestionale.controllers;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import it.squadra.gestionale.dto.ScoresheetDto.EventRequest;
import it.squadra.gestionale.dto.ScoresheetDto.ResultRequest;
import it.squadra.gestionale.dto.ScoresheetDto.ScoresheetResponse;
import it.squadra.gestionale.security.UserJwtAuthenticationConverter.AuthenticatedUser;
import it.squadra.gestionale.services.ScoresheetService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Risultato ufficiale e tabellino: lettura per tutti, modifiche solo per l'Admin.
 * Ogni modifica restituisce il tabellino aggiornato, con il controllo di coerenza.
 */
@Tag(name = "Risultati e tabellini")
@RestController
@RequestMapping("/api/v1/matches/{matchId}")
@PreAuthorize("hasRole('PLAYER')")
public class ScoresheetController {

    private final ScoresheetService scoresheetService;

    public ScoresheetController(ScoresheetService scoresheetService) {
        this.scoresheetService = scoresheetService;
    }

    @Operation(summary = "Risultato, eventi e controllo di coerenza")
    @GetMapping("/scoresheet")
    public ScoresheetResponse get(@PathVariable Long matchId) {
        return scoresheetService.get(matchId);
    }

    @Operation(summary = "Registra o modifica il risultato ufficiale (Admin); la partita diventa conclusa")
    @PutMapping("/result")
    @PreAuthorize("hasRole('ADMIN')")
    public ScoresheetResponse recordResult(@PathVariable Long matchId, @Valid @RequestBody ResultRequest request,
                                           @AuthenticationPrincipal AuthenticatedUser user, HttpServletRequest http) {
        return scoresheetService.recordResult(matchId, request, user.id(), http.getRemoteAddr());
    }

    @Operation(summary = "Aggiunge un evento al tabellino (Admin)")
    @PostMapping("/events")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public ScoresheetResponse addEvent(@PathVariable Long matchId, @Valid @RequestBody EventRequest request,
                                       @AuthenticationPrincipal AuthenticatedUser user, HttpServletRequest http) {
        return scoresheetService.addEvent(matchId, request, user.id(), http.getRemoteAddr());
    }

    @Operation(summary = "Modifica un evento del tabellino (Admin); richiede la versione")
    @PutMapping("/events/{eventId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ScoresheetResponse updateEvent(@PathVariable Long matchId, @PathVariable Long eventId,
                                          @Valid @RequestBody EventRequest request,
                                          @AuthenticationPrincipal AuthenticatedUser user, HttpServletRequest http) {
        return scoresheetService.updateEvent(matchId, eventId, request, user.id(), http.getRemoteAddr());
    }

    @Operation(summary = "Elimina un evento inserito per errore (Admin); resta traccia nell'audit log")
    @DeleteMapping("/events/{eventId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ScoresheetResponse deleteEvent(@PathVariable Long matchId, @PathVariable Long eventId,
                                          @AuthenticationPrincipal AuthenticatedUser user, HttpServletRequest http) {
        return scoresheetService.deleteEvent(matchId, eventId, user.id(), http.getRemoteAddr());
    }
}

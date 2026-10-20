package it.squadra.gestionale.controllers;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import it.squadra.gestionale.dto.CommonDto.PageResponse;
import it.squadra.gestionale.dto.MatchDto.MatchResponse;
import it.squadra.gestionale.dto.ScoresheetDto.EventRequest;
import it.squadra.gestionale.dto.ScoresheetDto.ResultRequest;
import it.squadra.gestionale.dto.ScoresheetDto.ScoresheetResponse;
import it.squadra.gestionale.dto.ScoresheetDto.StatsResponse;
import it.squadra.gestionale.security.UserJwtAuthenticationConverter.AuthenticatedUser;
import it.squadra.gestionale.services.MatchService;
import it.squadra.gestionale.services.ScoresheetService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
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
 * Risultato ufficiale, tabellino e storico dei risultati: lettura per tutti, modifiche solo per l'Admin.
 * Ogni modifica restituisce il tabellino aggiornato, con il controllo di coerenza.
 */
@Tag(name = "Risultati e tabellini")
@RestController
@RequestMapping("/api/v1/matches")
@PreAuthorize("hasRole('PLAYER')")
public class ScoresheetController {

    private final ScoresheetService scoresheetService;
    private final MatchService matchService;

    public ScoresheetController(ScoresheetService scoresheetService, MatchService matchService) {
        this.scoresheetService = scoresheetService;
        this.matchService = matchService;
    }

    @Operation(summary = "Storico dei risultati: partite concluse, dalla più recente")
    @GetMapping("/results")
    public PageResponse<MatchResponse> results(@PageableDefault(size = 10) Pageable pageable) {
        return PageResponse.of(scoresheetService.results(pageable), m -> MatchResponse.from(m, matchService.zone()));
    }

    @Operation(summary = "Bilancio della squadra e statistiche dei giocatori sulle partite concluse")
    @GetMapping("/stats")
    public StatsResponse stats() {
        return scoresheetService.stats();
    }

    @Operation(summary = "Risultato, eventi e controllo di coerenza")
    @GetMapping("/{matchId}/scoresheet")
    public ScoresheetResponse get(@PathVariable Long matchId) {
        return scoresheetService.get(matchId);
    }

    @Operation(summary = "Registra o modifica il risultato ufficiale (Admin); la partita diventa conclusa")
    @PutMapping("/{matchId}/result")
    @PreAuthorize("hasRole('ADMIN')")
    public ScoresheetResponse recordResult(@PathVariable Long matchId, @Valid @RequestBody ResultRequest request,
                                           @AuthenticationPrincipal AuthenticatedUser user, HttpServletRequest http) {
        return scoresheetService.recordResult(matchId, request, user.id(), http.getRemoteAddr());
    }

    @Operation(summary = "Aggiunge un evento al tabellino (Admin)")
    @PostMapping("/{matchId}/events")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public ScoresheetResponse addEvent(@PathVariable Long matchId, @Valid @RequestBody EventRequest request,
                                       @AuthenticationPrincipal AuthenticatedUser user, HttpServletRequest http) {
        return scoresheetService.addEvent(matchId, request, user.id(), http.getRemoteAddr());
    }

    @Operation(summary = "Modifica un evento del tabellino (Admin); richiede la versione")
    @PutMapping("/{matchId}/events/{eventId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ScoresheetResponse updateEvent(@PathVariable Long matchId, @PathVariable Long eventId,
                                          @Valid @RequestBody EventRequest request,
                                          @AuthenticationPrincipal AuthenticatedUser user, HttpServletRequest http) {
        return scoresheetService.updateEvent(matchId, eventId, request, user.id(), http.getRemoteAddr());
    }

    @Operation(summary = "Elimina un evento inserito per errore (Admin); resta traccia nell'audit log")
    @DeleteMapping("/{matchId}/events/{eventId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ScoresheetResponse deleteEvent(@PathVariable Long matchId, @PathVariable Long eventId,
                                          @AuthenticationPrincipal AuthenticatedUser user, HttpServletRequest http) {
        return scoresheetService.deleteEvent(matchId, eventId, user.id(), http.getRemoteAddr());
    }
}

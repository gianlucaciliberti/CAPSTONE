package it.squadra.gestionale.controllers;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import it.squadra.gestionale.dto.CommonDto.PageResponse;
import it.squadra.gestionale.dto.MatchDto.CancelMatchRequest;
import it.squadra.gestionale.dto.MatchDto.MatchRequest;
import it.squadra.gestionale.dto.MatchDto.MatchResponse;
import it.squadra.gestionale.security.UserJwtAuthenticationConverter.AuthenticatedUser;
import it.squadra.gestionale.services.MatchService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.time.YearMonth;
import java.util.List;
import org.springframework.data.domain.Pageable;
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

/** Calendario: lettura per tutti gli utenti attivi, modifiche solo per l'Admin. */
@Tag(name = "Partite")
@RestController
@RequestMapping("/api/v1/matches")
@PreAuthorize("hasRole('PLAYER')")
public class MatchController {

    private final MatchService matchService;

    public MatchController(MatchService matchService) {
        this.matchService = matchService;
    }

    @Operation(summary = "Partite di un mese (formato yyyy-MM, ora italiana), comprese quelle annullate")
    @GetMapping
    public List<MatchResponse> month(@RequestParam YearMonth month) {
        return matchService.month(month).stream().map(m -> MatchResponse.from(m, matchService.zone())).toList();
    }

    @Operation(summary = "Prossime partite programmate (da oggi in poi)")
    @GetMapping("/upcoming")
    public PageResponse<MatchResponse> upcoming(@PageableDefault(size = 5) Pageable pageable) {
        return PageResponse.of(matchService.upcoming(pageable), m -> MatchResponse.from(m, matchService.zone()));
    }

    @Operation(summary = "Dettaglio di una partita")
    @GetMapping("/{id}")
    public MatchResponse get(@PathVariable Long id) {
        return MatchResponse.from(matchService.get(id), matchService.zone());
    }

    @Operation(summary = "Crea una partita (Admin)")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public MatchResponse create(@Valid @RequestBody MatchRequest request,
                                @AuthenticationPrincipal AuthenticatedUser actor, HttpServletRequest http) {
        return MatchResponse.from(matchService.create(request, actor.id(), http.getRemoteAddr()), matchService.zone());
    }

    @Operation(summary = "Modifica una partita (Admin); richiede la versione letta in precedenza")
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public MatchResponse update(@PathVariable Long id, @Valid @RequestBody MatchRequest request,
                                @AuthenticationPrincipal AuthenticatedUser actor, HttpServletRequest http) {
        return MatchResponse.from(matchService.update(id, request, actor.id(), http.getRemoteAddr()),
                matchService.zone());
    }

    @Operation(summary = "Annulla una partita programmata (Admin); resta nello storico")
    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasRole('ADMIN')")
    public MatchResponse cancel(@PathVariable Long id, @Valid @RequestBody CancelMatchRequest request,
                                @AuthenticationPrincipal AuthenticatedUser actor, HttpServletRequest http) {
        return MatchResponse.from(matchService.cancel(id, request.reason(), request.version(), actor.id(),
                http.getRemoteAddr()), matchService.zone());
    }
}

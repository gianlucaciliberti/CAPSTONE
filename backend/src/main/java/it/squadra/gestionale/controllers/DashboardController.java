package it.squadra.gestionale.controllers;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import it.squadra.gestionale.dto.DashboardDto.DashboardResponse;
import it.squadra.gestionale.security.UserJwtAuthenticationConverter.AuthenticatedUser;
import it.squadra.gestionale.services.DashboardService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Dashboard della home: sempre sull'utente autenticato; le cose da fare dello staff dipendono dal ruolo letto dal database. */
@Tag(name = "Dashboard")
@RestController
@RequestMapping("/api/v1/dashboard")
@PreAuthorize("hasRole('PLAYER')")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @Operation(summary = "Prossima partita, ultimo risultato, pagamenti e (per lo staff) cose da fare")
    @GetMapping
    public DashboardResponse get(@AuthenticationPrincipal AuthenticatedUser user) {
        return dashboardService.get(user.id(), user.role());
    }
}

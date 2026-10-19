package it.squadra.gestionale.controllers;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import it.squadra.gestionale.dto.CommonDto.PageResponse;
import it.squadra.gestionale.dto.UserDto.ChangeRoleRequest;
import it.squadra.gestionale.dto.UserDto.UserResponse;
import it.squadra.gestionale.entities.User.AccountStatus;
import it.squadra.gestionale.security.UserJwtAuthenticationConverter.AuthenticatedUser;
import it.squadra.gestionale.services.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Gestione degli utenti: riservata agli Admin. */
@Tag(name = "Utenti (Admin)")
@RestController
@RequestMapping("/api/v1/users")
@PreAuthorize("hasRole('ADMIN')")
public class UserAdminController {

    private final UserService userService;

    public UserAdminController(UserService userService) {
        this.userService = userService;
    }

    @Operation(summary = "Elenco paginato degli utenti, filtrabile per stato")
    @GetMapping
    public PageResponse<UserResponse> list(@RequestParam(required = false) AccountStatus status,
                                           @PageableDefault(size = 20, sort = {"lastName", "firstName"})
                                           Pageable pageable) {
        return PageResponse.of(userService.list(status, pageable), UserResponse::from);
    }

    @Operation(summary = "Dettaglio di un utente")
    @GetMapping("/{id}")
    public UserResponse get(@PathVariable Long id) {
        return UserResponse.from(userService.getById(id));
    }

    @Operation(summary = "Approva una registrazione in attesa")
    @PostMapping("/{id}/approve")
    public UserResponse approve(@PathVariable Long id, @AuthenticationPrincipal AuthenticatedUser actor,
                                HttpServletRequest http) {
        return UserResponse.from(userService.approve(id, actor.id(), http.getRemoteAddr()));
    }

    @Operation(summary = "Rifiuta una registrazione in attesa")
    @PostMapping("/{id}/reject")
    public UserResponse reject(@PathVariable Long id, @AuthenticationPrincipal AuthenticatedUser actor,
                               HttpServletRequest http) {
        return UserResponse.from(userService.reject(id, actor.id(), http.getRemoteAddr()));
    }

    @Operation(summary = "Sospende un utente attivo (chiude tutte le sue sessioni)")
    @PostMapping("/{id}/suspend")
    public UserResponse suspend(@PathVariable Long id, @AuthenticationPrincipal AuthenticatedUser actor,
                                HttpServletRequest http) {
        return UserResponse.from(userService.suspend(id, actor.id(), http.getRemoteAddr()));
    }

    @Operation(summary = "Riattiva un utente sospeso o rifiutato")
    @PostMapping("/{id}/reactivate")
    public UserResponse reactivate(@PathVariable Long id, @AuthenticationPrincipal AuthenticatedUser actor,
                                   HttpServletRequest http) {
        return UserResponse.from(userService.reactivate(id, actor.id(), http.getRemoteAddr()));
    }

    @Operation(summary = "Modifica il ruolo di un utente")
    @PutMapping("/{id}/role")
    public UserResponse changeRole(@PathVariable Long id, @Valid @RequestBody ChangeRoleRequest request,
                                   @AuthenticationPrincipal AuthenticatedUser actor, HttpServletRequest http) {
        return UserResponse.from(userService.changeRole(id, request.role(), actor.id(), http.getRemoteAddr()));
    }
}

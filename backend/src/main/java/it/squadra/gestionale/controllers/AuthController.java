package it.squadra.gestionale.controllers;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import it.squadra.gestionale.dto.MessageResponse;
import it.squadra.gestionale.dto.RegisterRequest;
import it.squadra.gestionale.dto.UserResponse;
import it.squadra.gestionale.security.AuthenticatedUser;
import it.squadra.gestionale.services.RegistrationService;
import it.squadra.gestionale.services.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Autenticazione")
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    static final String REGISTRATION_MESSAGE =
            "Richiesta di registrazione ricevuta. Potrai accedere dopo l'approvazione di un amministratore.";

    private final UserService userService;
    private final RegistrationService registrationService;

    public AuthController(UserService userService, RegistrationService registrationService) {
        this.userService = userService;
        this.registrationService = registrationService;
    }

    @Operation(summary = "Registrazione pubblica (account PLAYER in attesa di approvazione)")
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public MessageResponse register(@Valid @RequestBody RegisterRequest request, HttpServletRequest http) {
        registrationService.register(request, http.getRemoteAddr());
        // Stessa risposta anche se l'email esiste già, per non rivelare quali utenti sono registrati.
        return new MessageResponse(REGISTRATION_MESSAGE);
    }

    @Operation(summary = "Dati dell'utente autenticato")
    @GetMapping("/me")
    public UserResponse me(@AuthenticationPrincipal AuthenticatedUser principal) {
        return UserResponse.from(userService.getById(principal.id()));
    }
}

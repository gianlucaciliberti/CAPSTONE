package it.squadra.gestionale.controllers;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import it.squadra.gestionale.dto.LoginRequest;
import it.squadra.gestionale.dto.LoginResponse;
import it.squadra.gestionale.dto.MessageResponse;
import it.squadra.gestionale.dto.RegisterRequest;
import it.squadra.gestionale.dto.UserResponse;
import it.squadra.gestionale.exceptions.ApiException;
import it.squadra.gestionale.security.AuthenticatedUser;
import it.squadra.gestionale.security.CsrfProtection;
import it.squadra.gestionale.security.RefreshTokenCookies;
import it.squadra.gestionale.services.AuthService;
import it.squadra.gestionale.services.AuthService.LoginResult;
import it.squadra.gestionale.services.RegistrationService;
import it.squadra.gestionale.services.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CookieValue;
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
    private final AuthService authService;
    private final RefreshTokenCookies refreshTokenCookies;
    private final CsrfProtection csrfProtection;

    public AuthController(UserService userService, RegistrationService registrationService, AuthService authService,
                          RefreshTokenCookies refreshTokenCookies, CsrfProtection csrfProtection) {
        this.userService = userService;
        this.registrationService = registrationService;
        this.authService = authService;
        this.refreshTokenCookies = refreshTokenCookies;
        this.csrfProtection = csrfProtection;
    }

    @Operation(summary = "Registrazione pubblica (account PLAYER in attesa di approvazione)")
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public MessageResponse register(@Valid @RequestBody RegisterRequest request, HttpServletRequest http) {
        registrationService.register(request, http.getRemoteAddr());
        // Stessa risposta anche se l'email esiste già, per non rivelare quali utenti sono registrati.
        return new MessageResponse(REGISTRATION_MESSAGE);
    }

    @Operation(summary = "Login: restituisce l'access token e imposta il cookie HttpOnly del refresh token")
    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest http,
                               HttpServletResponse response) {
        LoginResult result = authService.login(request.email(), request.password(),
                http.getHeader(HttpHeaders.USER_AGENT), http.getRemoteAddr());
        return toResponse(result, response);
    }

    @Operation(summary = "Rinnovo dell'access token tramite il cookie del refresh token (richiede X-Requested-With)")
    @PostMapping("/refresh")
    public LoginResponse refresh(@CookieValue(name = RefreshTokenCookies.COOKIE_NAME, required = false) String refreshToken,
                                 HttpServletRequest http, HttpServletResponse response) {
        csrfProtection.requireCustomHeader(http);
        try {
            LoginResult result = authService.refresh(refreshToken, http.getHeader(HttpHeaders.USER_AGENT),
                    http.getRemoteAddr());
            return toResponse(result, response);
        } catch (ApiException e) {
            // Sessione non più valida: si elimina anche il cookie dal browser.
            refreshTokenCookies.clear(response);
            throw e;
        }
    }

    @Operation(summary = "Logout: chiude la sessione corrente e cancella il cookie (richiede X-Requested-With)")
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@CookieValue(name = RefreshTokenCookies.COOKIE_NAME, required = false) String refreshToken,
                       HttpServletRequest http, HttpServletResponse response) {
        csrfProtection.requireCustomHeader(http);
        authService.logout(refreshToken);
        refreshTokenCookies.clear(response);
    }

    @Operation(summary = "Dati dell'utente autenticato")
    @GetMapping("/me")
    public UserResponse me(@AuthenticationPrincipal AuthenticatedUser principal) {
        return UserResponse.from(userService.getById(principal.id()));
    }

    private LoginResponse toResponse(LoginResult result, HttpServletResponse response) {
        refreshTokenCookies.write(response, result.refreshToken().value(), result.refreshToken().expiresAt());
        return new LoginResponse(result.accessToken().value(), result.accessToken().expiresAt(),
                UserResponse.from(result.user()));
    }
}

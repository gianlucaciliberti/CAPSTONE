package it.squadra.gestionale.controllers;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import it.squadra.gestionale.dto.AuthDto.LoginRequest;
import it.squadra.gestionale.dto.AuthDto.LoginResponse;
import it.squadra.gestionale.dto.AuthDto.RegisterRequest;
import it.squadra.gestionale.dto.CommonDto.MessageResponse;
import it.squadra.gestionale.dto.UserDto.UserResponse;
import it.squadra.gestionale.exceptions.ApiException;
import it.squadra.gestionale.security.AuthTokens;
import it.squadra.gestionale.security.UserJwtAuthenticationConverter.AuthenticatedUser;
import it.squadra.gestionale.services.AuthService;
import it.squadra.gestionale.services.AuthService.LoginResult;
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
    private final AuthService authService;
    private final AuthTokens authTokens;

    public AuthController(UserService userService, AuthService authService, AuthTokens authTokens) {
        this.userService = userService;
        this.authService = authService;
        this.authTokens = authTokens;
    }

    @Operation(summary = "Registrazione pubblica (account PLAYER in attesa di approvazione)")
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public MessageResponse register(@Valid @RequestBody RegisterRequest request, HttpServletRequest http) {
        authService.register(request, http.getRemoteAddr());
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
    public LoginResponse refresh(@CookieValue(name = AuthTokens.COOKIE_NAME, required = false) String refreshToken,
                                 HttpServletRequest http, HttpServletResponse response) {
        authTokens.requireCsrfHeader(http);
        try {
            LoginResult result = authService.refresh(refreshToken, http.getHeader(HttpHeaders.USER_AGENT),
                    http.getRemoteAddr());
            return toResponse(result, response);
        } catch (ApiException e) {
            // Sessione non più valida: si elimina anche il cookie dal browser.
            authTokens.clearRefreshCookie(response);
            throw e;
        }
    }

    @Operation(summary = "Logout: chiude la sessione corrente e cancella il cookie (richiede X-Requested-With)")
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@CookieValue(name = AuthTokens.COOKIE_NAME, required = false) String refreshToken,
                       HttpServletRequest http, HttpServletResponse response) {
        authTokens.requireCsrfHeader(http);
        authService.logout(refreshToken);
        authTokens.clearRefreshCookie(response);
    }

    @Operation(summary = "Dati dell'utente autenticato")
    @GetMapping("/me")
    public UserResponse me(@AuthenticationPrincipal AuthenticatedUser principal) {
        return UserResponse.from(userService.getById(principal.id()));
    }

    private LoginResponse toResponse(LoginResult result, HttpServletResponse response) {
        authTokens.writeRefreshCookie(response, result.refreshToken().value(), result.refreshToken().expiresAt());
        return new LoginResponse(result.accessToken().value(), result.accessToken().expiresAt(),
                UserResponse.from(result.user()));
    }
}

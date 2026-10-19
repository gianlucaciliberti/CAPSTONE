package it.squadra.gestionale.dto;

import it.squadra.gestionale.dto.UserDto.UserResponse;
import it.squadra.gestionale.validation.StrongPassword;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;

/** DTO degli endpoint di autenticazione (/api/v1/auth). */
public final class AuthDto {

    private AuthDto() {
    }

    /**
     * Registrazione pubblica. Contiene solo i dati che l'utente può scegliere:
     * ruolo e stato sono imposti dal server (PLAYER / PENDING).
     */
    public record RegisterRequest(
            @NotBlank(message = "Il nome è obbligatorio.")
            @Size(max = 80, message = "Il nome può avere al massimo 80 caratteri.")
            String firstName,

            @NotBlank(message = "Il cognome è obbligatorio.")
            @Size(max = 80, message = "Il cognome può avere al massimo 80 caratteri.")
            String lastName,

            @NotBlank(message = "L'email è obbligatoria.")
            @Email(message = "Inserisci un indirizzo email valido.")
            @Size(max = 254, message = "L'email può avere al massimo 254 caratteri.")
            String email,

            @StrongPassword
            String password) {
    }

    public record LoginRequest(
            @NotBlank(message = "L'email è obbligatoria.")
            @Size(max = 254, message = "L'email può avere al massimo 254 caratteri.")
            String email,

            @NotBlank(message = "La password è obbligatoria.")
            @Size(max = 200, message = "La password è troppo lunga.")
            String password) {
    }

    public record ForgotPasswordRequest(
            @NotBlank(message = "L'email è obbligatoria.")
            @Email(message = "Inserisci un indirizzo email valido.")
            @Size(max = 254, message = "L'email può avere al massimo 254 caratteri.")
            String email) {
    }

    public record ResetPasswordRequest(
            @NotBlank(message = "Il link di reimpostazione non è valido.")
            @Size(max = 100, message = "Il link di reimpostazione non è valido.")
            String token,

            @StrongPassword
            String newPassword) {
    }

    /**
     * Risposta di login/rinnovo. L'access token va tenuto solo in memoria dal frontend;
     * il refresh token NON compare qui: viaggia esclusivamente nel cookie HttpOnly.
     */
    public record LoginResponse(String accessToken, String tokenType, Instant expiresAt, UserResponse user) {

        public LoginResponse(String accessToken, Instant expiresAt, UserResponse user) {
            this(accessToken, "Bearer", expiresAt, user);
        }
    }
}

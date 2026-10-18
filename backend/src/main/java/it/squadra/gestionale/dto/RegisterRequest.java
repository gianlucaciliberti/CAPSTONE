package it.squadra.gestionale.dto;

import it.squadra.gestionale.validation.StrongPassword;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

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

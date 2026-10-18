package it.squadra.gestionale.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotBlank(message = "L'email è obbligatoria.")
        @Size(max = 254, message = "L'email può avere al massimo 254 caratteri.")
        String email,

        @NotBlank(message = "La password è obbligatoria.")
        @Size(max = 200, message = "La password è troppo lunga.")
        String password) {
}

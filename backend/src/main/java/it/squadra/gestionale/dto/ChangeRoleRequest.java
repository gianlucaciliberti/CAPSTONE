package it.squadra.gestionale.dto;

import it.squadra.gestionale.enums.Role;
import jakarta.validation.constraints.NotNull;

public record ChangeRoleRequest(@NotNull(message = "Il ruolo è obbligatorio.") Role role) {
}

package it.squadra.gestionale.dto;

import it.squadra.gestionale.entities.User;
import it.squadra.gestionale.entities.User.AccountStatus;
import it.squadra.gestionale.entities.User.Role;
import jakarta.validation.constraints.NotNull;

/** DTO degli utenti (/api/v1/users e /api/v1/auth/me). */
public final class UserDto {

    private UserDto() {
    }

    /** Dati pubblici di un utente restituiti dalle API (mai hash della password o contatori di sicurezza). */
    public record UserResponse(Long id, String firstName, String lastName, String email, Role role,
                               AccountStatus status, boolean emailVerified) {

        public static UserResponse from(User user) {
            return new UserResponse(user.getId(), user.getFirstName(), user.getLastName(), user.getEmail(),
                    user.getRole(), user.getStatus(), user.getEmailVerifiedAt() != null);
        }
    }

    public record ChangeRoleRequest(@NotNull(message = "Il ruolo è obbligatorio.") Role role) {
    }
}

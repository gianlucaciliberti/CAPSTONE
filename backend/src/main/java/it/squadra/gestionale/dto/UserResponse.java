package it.squadra.gestionale.dto;

import it.squadra.gestionale.entities.User;
import it.squadra.gestionale.enums.AccountStatus;
import it.squadra.gestionale.enums.Role;

/** Dati pubblici di un utente restituiti dalle API (mai hash della password o contatori di sicurezza). */
public record UserResponse(Long id, String firstName, String lastName, String email, Role role,
                           AccountStatus status, boolean emailVerified) {

    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getFirstName(), user.getLastName(), user.getEmail(),
                user.getRole(), user.getStatus(), user.getEmailVerifiedAt() != null);
    }
}

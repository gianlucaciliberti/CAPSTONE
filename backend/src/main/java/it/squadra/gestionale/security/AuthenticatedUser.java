package it.squadra.gestionale.security;

import it.squadra.gestionale.enums.Role;

/**
 * Utente autenticato della richiesta corrente, ricostruito dal database (non dal contenuto del token).
 * Si ottiene nei controller con {@code @AuthenticationPrincipal AuthenticatedUser user}.
 */
public record AuthenticatedUser(Long id, String email, Role role) {
}

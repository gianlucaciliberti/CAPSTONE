package it.squadra.gestionale.security;

import it.squadra.gestionale.entities.User;
import it.squadra.gestionale.entities.User.Role;
import it.squadra.gestionale.repositories.UserRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/**
 * Trasforma un access token già verificato (firma, scadenza, emittente) nell'utente autenticato.
 * L'utente viene riletto dal database a ogni richiesta: ruolo e stato attuali prevalgono su qualsiasi
 * informazione del token, e un account non più ACTIVE viene respinto con 401.
 */
@Component
public class UserJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final UserRepository users;

    public UserJwtAuthenticationConverter(UserRepository users) {
        this.users = users;
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        User user = parseUserId(jwt).flatMap(users::findById)
                .orElseThrow(() -> new BadCredentialsException("Utente del token inesistente"));
        if (!user.isActive()) {
            throw new DisabledException("Account non attivo");
        }
        AuthenticatedUser principal = new AuthenticatedUser(user.getId(), user.getEmail(), user.getRole());
        // Solo il ruolo effettivo: i ruoli inclusi (ADMIN > COACH > PLAYER) li aggiunge la RoleHierarchy.
        return UsernamePasswordAuthenticationToken.authenticated(principal, null,
                List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())));
    }

    private static Optional<Long> parseUserId(Jwt jwt) {
        try {
            return Optional.of(Long.valueOf(jwt.getSubject()));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    /**
     * Utente autenticato della richiesta corrente, ricostruito dal database (non dal contenuto del token).
     * Si ottiene nei controller con {@code @AuthenticationPrincipal AuthenticatedUser user}.
     */
    public record AuthenticatedUser(Long id, String email, Role role) {
    }
}

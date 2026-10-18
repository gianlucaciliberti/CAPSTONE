package it.squadra.gestionale.controllers;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import it.squadra.gestionale.entities.User;
import it.squadra.gestionale.enums.AccountStatus;
import it.squadra.gestionale.enums.Role;
import it.squadra.gestionale.repositories.UserRepository;
import it.squadra.gestionale.security.JwtService;
import it.squadra.gestionale.support.IntegrationTest;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@IntegrationTest
@Import(AuthenticationIntegrationTest.RoleProbeController.class)
class AuthenticationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository users;
    @Autowired
    private JwtService jwtService;

    @Test
    void meWithoutTokenIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void meReturnsCurrentUserWithoutSensitiveFields() throws Exception {
        User user = user("mario.me@example.com", Role.COACH, AccountStatus.ACTIVE);

        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", bearer(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(user.getId()))
                .andExpect(jsonPath("$.email").value("mario.me@example.com"))
                .andExpect(jsonPath("$.role").value("COACH"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void malformedTokenIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer non-un-jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @ParameterizedTest
    @CsvSource({"PENDING", "SUSPENDED", "REJECTED"})
    void validTokenOfInactiveAccountIsUnauthorized(AccountStatus status) throws Exception {
        User user = user("inattivo." + status.name().toLowerCase() + "@example.com", Role.PLAYER, status);

        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", bearer(user)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void roleChangeTakesEffectWithoutNewToken() throws Exception {
        User user = user("promosso@example.com", Role.PLAYER, AccountStatus.ACTIVE);
        String token = bearer(user);
        mockMvc.perform(get("/test/coach").header("Authorization", token)).andExpect(status().isForbidden());

        user.setRole(Role.COACH);
        users.saveAndFlush(user);

        mockMvc.perform(get("/test/coach").header("Authorization", token)).andExpect(status().isOk());
    }

    @ParameterizedTest(name = "{0} su /test/{1} -> {2}")
    @CsvSource({
            "PLAYER, player, 200",
            "PLAYER, coach,  403",
            "PLAYER, admin,  403",
            "COACH,  player, 200",
            "COACH,  coach,  200",
            "COACH,  admin,  403",
            "ADMIN,  player, 200",
            "ADMIN,  coach,  200",
            "ADMIN,  admin,  200"
    })
    void rolesAreCumulative(Role role, String endpoint, int expectedStatus) throws Exception {
        User user = user(role.name().toLowerCase() + "." + endpoint + "@example.com", role, AccountStatus.ACTIVE);

        mockMvc.perform(get("/test/" + endpoint).header("Authorization", bearer(user)))
                .andExpect(status().is(expectedStatus));
    }

    @Test
    void forbiddenResponseIsProblemDetail() throws Exception {
        User user = user("giocatore.403@example.com", Role.PLAYER, AccountStatus.ACTIVE);

        mockMvc.perform(get("/test/admin").header("Authorization", bearer(user)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    private User user(String email, Role role, AccountStatus status) {
        User user = new User("Test", "Utente", email, "hash");
        user.setRole(role);
        user.changeStatus(status, null, Instant.now());
        return users.saveAndFlush(user);
    }

    private String bearer(User user) {
        return "Bearer " + jwtService.issueAccessToken(user).value();
    }

    /** Endpoint usati solo dai test per verificare i permessi dei tre ruoli. */
    @RestController
    static class RoleProbeController {

        @PreAuthorize("hasRole('PLAYER')")
        @GetMapping("/test/player")
        String player() {
            return "ok";
        }

        @PreAuthorize("hasRole('COACH')")
        @GetMapping("/test/coach")
        String coach() {
            return "ok";
        }

        @PreAuthorize("hasRole('ADMIN')")
        @GetMapping("/test/admin")
        String admin() {
            return "ok";
        }
    }
}

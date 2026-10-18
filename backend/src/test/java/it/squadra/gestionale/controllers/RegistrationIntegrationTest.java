package it.squadra.gestionale.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import it.squadra.gestionale.entities.User;
import it.squadra.gestionale.enums.AccountStatus;
import it.squadra.gestionale.enums.Role;
import it.squadra.gestionale.repositories.UserRepository;
import it.squadra.gestionale.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@IntegrationTest
class RegistrationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository users;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void registrationCreatesPendingPlayerWithHashedPasswordAndAuditEntry() throws Exception {
        register("""
                {"firstName":" Giulia ","lastName":"Neri","email":"Giulia.Neri@Example.com","password":"Calcetto2026"}""")
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.message").value(AuthController.REGISTRATION_MESSAGE));

        User user = users.findByEmail("giulia.neri@example.com").orElseThrow();
        assertThat(user.getFirstName()).isEqualTo("Giulia");
        assertThat(user.getRole()).isEqualTo(Role.PLAYER);
        assertThat(user.getStatus()).isEqualTo(AccountStatus.PENDING);
        assertThat(user.getPasswordHash()).startsWith("$2").isNotEqualTo("Calcetto2026");
        assertThat(passwordEncoder.matches("Calcetto2026", user.getPasswordHash())).isTrue();

        Integer audits = jdbc.queryForObject(
                "select count(*) from audit_logs where action = 'USER_REGISTERED' and entity_id = ?",
                Integer.class, String.valueOf(user.getId()));
        assertThat(audits).isEqualTo(1);
    }

    @Test
    void privilegedFieldsSentByClientAreIgnored() throws Exception {
        register("""
                {"firstName":"Furbo","lastName":"Rossi","email":"furbo@example.com","password":"Calcetto2026",
                 "role":"ADMIN","status":"ACTIVE","id":1}""")
                .andExpect(status().isAccepted());

        User user = users.findByEmail("furbo@example.com").orElseThrow();
        assertThat(user.getRole()).isEqualTo(Role.PLAYER);
        assertThat(user.getStatus()).isEqualTo(AccountStatus.PENDING);
    }

    @Test
    void duplicateEmailGetsSameResponseAndDoesNotChangeExistingAccount() throws Exception {
        register("""
                {"firstName":"Primo","lastName":"Utente","email":"doppio@example.com","password":"Calcetto2026"}""")
                .andExpect(status().isAccepted());
        String originalHash = users.findByEmail("doppio@example.com").orElseThrow().getPasswordHash();

        register("""
                {"firstName":"Secondo","lastName":"Tentativo","email":"DOPPIO@example.com","password":"AltraPassword99"}""")
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.message").value(AuthController.REGISTRATION_MESSAGE));

        User user = users.findByEmail("doppio@example.com").orElseThrow();
        assertThat(user.getFirstName()).isEqualTo("Primo");
        assertThat(user.getPasswordHash()).isEqualTo(originalHash);
        assertThat(jdbc.queryForObject("select count(*) from users where email = 'doppio@example.com'", Integer.class))
                .isEqualTo(1);
    }

    @Test
    void invalidDataReturnsFieldErrorsInItalian() throws Exception {
        register("""
                {"firstName":"","lastName":"","email":"non-una-email","password":"corta"}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors.firstName").value("Il nome è obbligatorio."))
                .andExpect(jsonPath("$.fieldErrors.lastName").value("Il cognome è obbligatorio."))
                .andExpect(jsonPath("$.fieldErrors.email").value("Inserisci un indirizzo email valido."))
                .andExpect(jsonPath("$.fieldErrors.password").exists());
    }

    @Test
    void missingBodyIsBadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content("{malformato"))
                .andExpect(status().isBadRequest());
    }

    private ResultActions register(String json) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(json));
    }
}

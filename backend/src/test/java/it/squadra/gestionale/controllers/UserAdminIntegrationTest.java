package it.squadra.gestionale.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import it.squadra.gestionale.entities.User;
import it.squadra.gestionale.entities.User.AccountStatus;
import it.squadra.gestionale.entities.User.Role;
import it.squadra.gestionale.exceptions.ApiException;
import it.squadra.gestionale.repositories.UserRepository;
import it.squadra.gestionale.security.JwtService;
import it.squadra.gestionale.services.RefreshTokenService;
import it.squadra.gestionale.support.IntegrationTest;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@IntegrationTest
class UserAdminIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository users;
    @Autowired
    private JwtService jwtService;
    @Autowired
    private RefreshTokenService refreshTokenService;
    @Autowired
    private JdbcTemplate jdbc;

    private User admin;

    @BeforeEach
    void createAdmin() {
        admin = user("admin@example.com", Role.ADMIN, AccountStatus.ACTIVE);
    }

    @Test
    void nonAdminsCannotManageUsers() throws Exception {
        User coach = user("coach@example.com", Role.COACH, AccountStatus.ACTIVE);
        User pending = user("attesa@example.com", Role.PLAYER, AccountStatus.PENDING);

        mockMvc.perform(get("/api/v1/users").header("Authorization", bearer(coach)))
                .andExpect(status().isForbidden());
        perform(coach, post("/api/v1/users/" + pending.getId() + "/approve")).andExpect(status().isForbidden());
        assertThat(reload(pending).getStatus()).isEqualTo(AccountStatus.PENDING);
    }

    @Test
    void listFiltersByStatusWithPagination() throws Exception {
        user("attesa1@example.com", Role.PLAYER, AccountStatus.PENDING);
        user("attesa2@example.com", Role.PLAYER, AccountStatus.PENDING);
        user("attivo@example.com", Role.PLAYER, AccountStatus.ACTIVE);

        mockMvc.perform(get("/api/v1/users?status=PENDING&size=1").header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].status").value("PENDING"))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(2));
    }

    @Test
    void invalidSortIsBadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/users?sort=inesistente").header("Authorization", bearer(admin)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void approvePendingUserRecordsAuthorAndAudit() throws Exception {
        User pending = user("nuovo@example.com", Role.PLAYER, AccountStatus.PENDING);

        perform(admin, post("/api/v1/users/" + pending.getId() + "/approve"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        User reloaded = reload(pending);
        assertThat(reloaded.getStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(reloaded.getStatusChangedBy().getId()).isEqualTo(admin.getId());
        assertThat(auditCount("USER_APPROVED", pending)).isEqualTo(1);
    }

    @Test
    void invalidTransitionIsRejected() throws Exception {
        User active = user("gia.attivo@example.com", Role.PLAYER, AccountStatus.ACTIVE);

        perform(admin, post("/api/v1/users/" + active.getId() + "/approve"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("BUSINESS_RULE_VIOLATION"));
        perform(admin, post("/api/v1/users/" + active.getId() + "/reject"))
                .andExpect(status().isUnprocessableContent());
    }

    @Test
    void suspendClosesAllSessionsAndReactivateRestoresAccess() throws Exception {
        User player = user("sospeso@example.com", Role.PLAYER, AccountStatus.ACTIVE);
        String session = refreshTokenService.createSession(player, "JUnit", "127.0.0.1").value();

        perform(admin, post("/api/v1/users/" + player.getId() + "/suspend"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUSPENDED"));

        assertThatThrownBy(() -> refreshTokenService.rotate(session, "JUnit", "127.0.0.1"))
                .isInstanceOf(ApiException.class);
        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", bearer(player)))
                .andExpect(status().isUnauthorized());

        perform(admin, post("/api/v1/users/" + player.getId() + "/reactivate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
        assertThat(auditCount("USER_SUSPENDED", player)).isEqualTo(1);
        assertThat(auditCount("USER_REACTIVATED", player)).isEqualTo(1);
    }

    @Test
    void changeRoleIsAuditedWithBeforeAndAfter() throws Exception {
        User player = user("promosso@example.com", Role.PLAYER, AccountStatus.ACTIVE);

        perform(admin, put("/api/v1/users/" + player.getId() + "/role")
                .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"COACH\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("COACH"));

        String details = jdbc.queryForObject(
                "select details::text from audit_logs where action = 'USER_ROLE_CHANGED' and entity_id = ?",
                String.class, String.valueOf(player.getId()));
        assertThat(details).contains("\"before\": \"PLAYER\"", "\"after\": \"COACH\"");
    }

    @Test
    void adminCannotChangeOwnStatus() throws Exception {
        perform(admin, post("/api/v1/users/" + admin.getId() + "/suspend"))
                .andExpect(status().isUnprocessableContent());
        assertThat(reload(admin).getStatus()).isEqualTo(AccountStatus.ACTIVE);
    }

    @Test
    void lastActiveAdminCannotBeDemoted() throws Exception {
        perform(admin, put("/api/v1/users/" + admin.getId() + "/role")
                .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"PLAYER\"}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value("Operazione non consentita: è l'ultimo amministratore attivo."));
        assertThat(reload(admin).getRole()).isEqualTo(Role.ADMIN);
    }

    @Test
    void adminCanBeDemotedOrSuspendedWhenAnotherActiveAdminExists() throws Exception {
        User secondAdmin = user("admin2@example.com", Role.ADMIN, AccountStatus.ACTIVE);

        perform(admin, post("/api/v1/users/" + secondAdmin.getId() + "/suspend")).andExpect(status().isOk());

        // Ora admin è di nuovo l'unico Admin attivo: non può declassarsi.
        perform(admin, put("/api/v1/users/" + admin.getId() + "/role")
                .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"COACH\"}"))
                .andExpect(status().isUnprocessableContent());
    }

    @Test
    void unknownUserIsNotFound() throws Exception {
        perform(admin, post("/api/v1/users/999999/approve")).andExpect(status().isNotFound());
    }

    private User user(String email, Role role, AccountStatus status) {
        User user = new User("Test", email.substring(0, email.indexOf('@')), email, "hash");
        user.setRole(role);
        user.changeStatus(status, null, Instant.now());
        return users.saveAndFlush(user);
    }

    private User reload(User user) {
        return users.findById(user.getId()).orElseThrow();
    }

    private String bearer(User user) {
        return "Bearer " + jwtService.issueAccessToken(user).value();
    }

    private ResultActions perform(User actor, MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request.header("Authorization", bearer(actor)));
    }

    private Integer auditCount(String action, User target) {
        return jdbc.queryForObject("select count(*) from audit_logs where action = ? and entity_id = ?",
                Integer.class, action, String.valueOf(target.getId()));
    }
}

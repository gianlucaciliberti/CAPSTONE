package it.squadra.gestionale.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import it.squadra.gestionale.config.BootstrapAdminProperties;
import it.squadra.gestionale.entities.User;
import it.squadra.gestionale.enums.AccountStatus;
import it.squadra.gestionale.enums.Role;
import it.squadra.gestionale.repositories.UserRepository;
import it.squadra.gestionale.support.IntegrationTest;
import jakarta.validation.Validator;
import java.time.Clock;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

@IntegrationTest
class AdminBootstrapServiceIntegrationTest {

    @Autowired
    private UserRepository users;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private Validator validator;
    @Autowired
    private AuditService auditService;
    @Autowired
    private Clock clock;
    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void createsActiveVerifiedAdminWhenNoneExists() {
        User admin = service("Primo.Admin@Example.com", "PasswordSicura2026", "Anna", "Bianchi")
                .createFirstAdminIfConfigured().orElseThrow();

        assertThat(admin.getEmail()).isEqualTo("primo.admin@example.com");
        assertThat(admin.getRole()).isEqualTo(Role.ADMIN);
        assertThat(admin.getStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(admin.getEmailVerifiedAt()).isNotNull();
        assertThat(passwordEncoder.matches("PasswordSicura2026", admin.getPasswordHash())).isTrue();
        assertThat(jdbc.queryForObject("select count(*) from audit_logs where action = 'ADMIN_BOOTSTRAPPED'",
                Integer.class)).isEqualTo(1);
    }

    @Test
    void doesNothingWhenNotConfigured() {
        assertThat(service(null, null, null, null).createFirstAdminIfConfigured()).isEmpty();
        assertThat(service("admin@example.com", "", null, null).createFirstAdminIfConfigured()).isEmpty();
        assertThat(users.existsByRole(Role.ADMIN)).isFalse();
    }

    @Test
    void doesNothingWhenAnAdminAlreadyExistsEvenIfSuspended() {
        User existing = new User("Vecchio", "Admin", "vecchio@example.com", "hash");
        existing.setRole(Role.ADMIN);
        existing.changeStatus(AccountStatus.SUSPENDED, null, Instant.now());
        users.saveAndFlush(existing);

        assertThat(service("nuovo.admin@example.com", "PasswordSicura2026", null, null)
                .createFirstAdminIfConfigured()).isEmpty();
        assertThat(users.findByEmail("nuovo.admin@example.com")).isEmpty();
    }

    @Test
    void weakPasswordOrInvalidEmailBlocksStartup() {
        assertThatThrownBy(() -> service("admin@example.com", "debole", null, null).createFirstAdminIfConfigured())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("password");
        assertThatThrownBy(() -> service("non-email", "PasswordSicura2026", null, null).createFirstAdminIfConfigured())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("email");
    }

    @Test
    void emailOfExistingUserIsRefused() {
        users.saveAndFlush(new User("Già", "Registrato", "occupata@example.com", "hash"));

        assertThatThrownBy(() -> service("occupata@example.com", "PasswordSicura2026", null, null)
                .createFirstAdminIfConfigured())
                .isInstanceOf(IllegalStateException.class);
    }

    private AdminBootstrapService service(String email, String password, String firstName, String lastName) {
        return new AdminBootstrapService(new BootstrapAdminProperties(email, password, firstName, lastName),
                users, passwordEncoder, validator, auditService, clock);
    }
}

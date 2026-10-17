package it.squadra.gestionale.repositories;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import it.squadra.gestionale.entities.AuditLog;
import it.squadra.gestionale.entities.Availability;
import it.squadra.gestionale.entities.Match;
import it.squadra.gestionale.entities.PaymentRequest;
import it.squadra.gestionale.entities.User;
import it.squadra.gestionale.support.IntegrationTest;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Verifica che le migrazioni Flyway producano lo schema atteso dalle entità (ddl-auto=validate all'avvio)
 * e che i vincoli più importanti siano applicati dal database stesso.
 */
@IntegrationTest
class SchemaIntegrationTest {

    @Autowired
    private UserRepository users;
    @Autowired
    private MatchRepository matches;
    @Autowired
    private AvailabilityRepository availabilities;
    @Autowired
    private PaymentRequestRepository paymentRequests;
    @Autowired
    private AuditLogRepository auditLogs;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private EntityManager entityManager;

    @Test
    void allMigrationsAreApplied() {
        Integer applied = jdbc.queryForObject(
                "select count(*) from flyway_schema_history where success = true and version is not null",
                Integer.class);
        assertThat(applied).isEqualTo(6);
    }

    @Test
    void newUserIsPendingPlayerWithNormalizedEmail() {
        User user = users.saveAndFlush(new User("Mario", "Rossi", "  Mario.Rossi@Example.COM ", "hash"));

        assertThat(user.getEmail()).isEqualTo("mario.rossi@example.com");
        assertThat(user.getRole().name()).isEqualTo("PLAYER");
        assertThat(user.getStatus().name()).isEqualTo("PENDING");
        assertThat(user.getCreatedAt()).isNotNull();
    }

    @Test
    void databaseRejectsUppercaseEmail() {
        assertThatThrownBy(() -> jdbc.update(
                "insert into users (first_name, last_name, email, password_hash) values ('A', 'B', 'Upper@Example.com', 'x')"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void databaseRejectsDuplicateAvailabilityForSameMatchAndUser() {
        User user = users.save(new User("Luca", "Bianchi", "luca@example.com", "hash"));
        Match match = matches.save(new Match("Avversari FC", Instant.parse("2026-11-07T14:30:00Z"),
                "Campo comunale", "Via Roma 1, Milano", null, user));
        availabilities.saveAndFlush(new Availability(match, user));

        assertThatThrownBy(() -> availabilities.saveAndFlush(new Availability(match, user)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void databaseRejectsNonPositivePaymentAmount() {
        User admin = users.save(new User("Anna", "Verdi", "anna@example.com", "hash"));

        assertThatThrownBy(() -> paymentRequests.saveAndFlush(
                new PaymentRequest("Quota", 0, LocalDate.of(2026, 12, 1), null, admin)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void auditLogStoresJsonDetailsAndIsAppendOnly() {
        AuditLog saved = auditLogs.saveAndFlush(new AuditLog(Instant.now(), null, "TEST_ACTION", "TEST", "1",
                "Prova", "{\"before\":null,\"after\":{\"value\":42}}", "127.0.0.1"));
        entityManager.clear();

        String value = jdbc.queryForObject("select details->'after'->>'value' from audit_logs where id = ?",
                String.class, saved.getId());
        assertThat(value).isEqualTo("42");

        assertThatThrownBy(() -> jdbc.update("update audit_logs set summary = 'manomesso' where id = ?", saved.getId()))
                .hasMessageContaining("append-only");
    }
}

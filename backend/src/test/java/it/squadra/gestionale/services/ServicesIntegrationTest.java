package it.squadra.gestionale.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import it.squadra.gestionale.config.AppProperties;
import it.squadra.gestionale.config.AppProperties.BootstrapAdmin;
import it.squadra.gestionale.entities.EmailNotification;
import it.squadra.gestionale.entities.EmailNotification.EmailStatus;
import it.squadra.gestionale.entities.EmailNotification.NotificationType;
import it.squadra.gestionale.entities.RefreshToken;
import it.squadra.gestionale.entities.User;
import it.squadra.gestionale.entities.User.AccountStatus;
import it.squadra.gestionale.entities.User.Role;
import it.squadra.gestionale.exceptions.ApiException;
import it.squadra.gestionale.repositories.EmailNotificationRepository;
import it.squadra.gestionale.repositories.RefreshTokenRepository;
import it.squadra.gestionale.repositories.UserRepository;
import it.squadra.gestionale.repositories.UserTokenRepository;
import it.squadra.gestionale.security.AuthTokens;
import it.squadra.gestionale.services.EmailSender.OutgoingEmail;
import it.squadra.gestionale.services.EmailService.EmailMessage;
import it.squadra.gestionale.services.RefreshTokenService.IssuedRefreshToken;
import it.squadra.gestionale.support.IntegrationTest;
import it.squadra.gestionale.support.MutableClock;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

/** Test di integrazione dei servizi su database. */
@IntegrationTest
class ServicesIntegrationTest {

    @Nested
    class RefreshTokens {

        @Autowired
        private RefreshTokenService service;
        @Autowired
        private RefreshTokenRepository tokens;
        @Autowired
        private UserRepository users;
        @Autowired
        private Clock clock;
        @Autowired
        private AppProperties properties;
        @Autowired
        private PlatformTransactionManager transactionManager;
        @Autowired
        private UserTokenRepository userTokens;

        private User user;

        @Test
        void purgeDeletesOnlyTokensExpiredBeforeRetention() {
            IssuedRefreshToken first = service.createSession(user, "JUnit", "127.0.0.1");
            service.rotate(first.value(), "JUnit", "127.0.0.1");
            Duration ttl = properties.security().jwt().refreshTokenTtl();

            RefreshTokenService soon = new RefreshTokenService(tokens, userTokens,
                    Clock.offset(clock, ttl.plusDays(1)), properties);
            assertThat(soon.purgeExpiredTokens()).isZero();

            RefreshTokenService later = new RefreshTokenService(tokens, userTokens,
                    Clock.offset(clock, ttl.plus(RefreshTokenService.PURGE_RETENTION).plusDays(1)), properties);
            // Entrambi i token della catena (quello ruotato e quello corrente) vengono eliminati insieme.
            assertThat(later.purgeExpiredTokens()).isEqualTo(2);
            assertThat(tokens.count()).isZero();
        }

        @BeforeEach
        void createActiveUser() {
            user = new User("Mario", "Rossi", "mario.refresh@example.com", "hash");
            user.changeStatus(AccountStatus.ACTIVE, null, Instant.now());
            user = users.save(user);
        }

        @Test
        void createSessionStoresOnlyTheHash() {
            IssuedRefreshToken issued = service.createSession(user, "JUnit", "127.0.0.1");

            RefreshToken stored = tokens.findByTokenHash(AuthTokens.hash(issued.value())).orElseThrow();
            assertThat(stored.getTokenHash()).hasSize(64).isNotEqualTo(issued.value());
            assertThat(stored.getUser().getId()).isEqualTo(user.getId());
            assertThat(issued.expiresAt()).isEqualTo(stored.getExpiresAt());
        }

        @Test
        void rotateRevokesOldTokenAndIssuesNewOneInSameFamily() {
            IssuedRefreshToken first = service.createSession(user, "JUnit", "127.0.0.1");

            IssuedRefreshToken second = service.rotate(first.value(), "JUnit", "127.0.0.1");

            RefreshToken old = tokens.findByTokenHash(AuthTokens.hash(first.value())).orElseThrow();
            RefreshToken current = tokens.findByTokenHash(AuthTokens.hash(second.value())).orElseThrow();
            assertThat(second.value()).isNotEqualTo(first.value());
            assertThat(old.wasRotated()).isTrue();
            assertThat(old.getReplacedBy().getId()).isEqualTo(current.getId());
            assertThat(current.getFamilyId()).isEqualTo(old.getFamilyId());
            assertThat(current.getRevokedAt()).isNull();
        }

        @Test
        void reusingRotatedTokenRevokesWholeFamilyWithoutRollingBack() {
            IssuedRefreshToken first = service.createSession(user, "JUnit", "127.0.0.1");
            IssuedRefreshToken second = service.rotate(first.value(), "JUnit", "127.0.0.1");

            assertThatThrownBy(() -> service.rotate(first.value(), "Attaccante", "10.0.0.1"))
                    .isInstanceOf(ApiException.class)
                    .extracting("status").isEqualTo(HttpStatus.UNAUTHORIZED);

            // La revoca deve sopravvivere all'eccezione: la transazione non è marcata per il rollback.
            TransactionStatus participating = transactionManager.getTransaction(new DefaultTransactionDefinition());
            assertThat(participating.isRollbackOnly()).isFalse();
            RefreshToken current = tokens.findByTokenHash(AuthTokens.hash(second.value())).orElseThrow();
            assertThat(current.getRevokedReason()).isEqualTo(RefreshTokenService.REASON_REUSE_DETECTED);
            assertThatThrownBy(() -> service.rotate(second.value(), "JUnit", "127.0.0.1"))
                    .isInstanceOf(ApiException.class);
        }

        @Test
        void expiredTokenIsRejected() {
            IssuedRefreshToken issued = service.createSession(user, "JUnit", "127.0.0.1");
            Clock afterExpiry = Clock.offset(clock, properties.security().jwt().refreshTokenTtl().plus(Duration.ofMinutes(1)));
            RefreshTokenService later = new RefreshTokenService(tokens, userTokens, afterExpiry, properties);

            assertThatThrownBy(() -> later.rotate(issued.value(), "JUnit", "127.0.0.1"))
                    .isInstanceOf(ApiException.class);
        }

        @Test
        void unknownOrMissingTokenIsRejected() {
            assertThatThrownBy(() -> service.rotate("token-inventato", null, null)).isInstanceOf(ApiException.class);
            assertThatThrownBy(() -> service.rotate(null, null, null)).isInstanceOf(ApiException.class);
        }

        @Test
        void suspendedUserCannotRenewSession() {
            IssuedRefreshToken issued = service.createSession(user, "JUnit", "127.0.0.1");
            user.changeStatus(AccountStatus.SUSPENDED, null, Instant.now());
            users.saveAndFlush(user);

            assertThatThrownBy(() -> service.rotate(issued.value(), "JUnit", "127.0.0.1"))
                    .isInstanceOf(ApiException.class);
            RefreshToken stored = tokens.findByTokenHash(AuthTokens.hash(issued.value())).orElseThrow();
            assertThat(stored.getRevokedReason()).isEqualTo(RefreshTokenService.REASON_ACCOUNT_INACTIVE);
        }

        @Test
        void logoutRevokesSessionAndIsIdempotent() {
            IssuedRefreshToken issued = service.createSession(user, "JUnit", "127.0.0.1");

            service.revoke(issued.value());
            service.revoke(issued.value());
            service.revoke("token-inventato");
            service.revoke(null);

            assertThatThrownBy(() -> service.rotate(issued.value(), "JUnit", "127.0.0.1"))
                    .isInstanceOf(ApiException.class);
        }

        @Test
        void revokeAllForUserClosesEverySession() {
            IssuedRefreshToken phone = service.createSession(user, "Telefono", "127.0.0.1");
            IssuedRefreshToken laptop = service.createSession(user, "Portatile", "127.0.0.1");

            int revoked = service.revokeAllForUser(user.getId(), "PASSWORD_CHANGED");

            assertThat(revoked).isEqualTo(2);
            for (String raw : List.of(phone.value(), laptop.value())) {
                assertThatThrownBy(() -> service.rotate(raw, "JUnit", "127.0.0.1")).isInstanceOf(ApiException.class);
            }
        }
    }

    @Nested
    class FirstAdmin {

        @Autowired
        private UserService userService;
        @Autowired
        private UserRepository users;
        @Autowired
        private PasswordEncoder passwordEncoder;
        @Autowired
        private JdbcTemplate jdbc;

        @Test
        void createsActiveVerifiedAdminWhenNoneExists() {
            User admin = bootstrap("Primo.Admin@Example.com", "PasswordSicura2026", "Anna", "Bianchi").orElseThrow();

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
            assertThat(bootstrap(null, null, null, null)).isEmpty();
            assertThat(bootstrap("admin@example.com", "", null, null)).isEmpty();
            // Nel profilo di test le variabili ADMIN_BOOTSTRAP_* sono sempre vuote.
            assertThat(userService.createFirstAdminIfConfigured()).isEmpty();
            assertThat(users.existsByRole(Role.ADMIN)).isFalse();
        }

        @Test
        void doesNothingWhenAnAdminAlreadyExistsEvenIfSuspended() {
            User existing = new User("Vecchio", "Admin", "vecchio@example.com", "hash");
            existing.setRole(Role.ADMIN);
            existing.changeStatus(AccountStatus.SUSPENDED, null, Instant.now());
            users.saveAndFlush(existing);

            assertThat(bootstrap("nuovo.admin@example.com", "PasswordSicura2026", null, null)).isEmpty();
            assertThat(users.findByEmail("nuovo.admin@example.com")).isEmpty();
        }

        @Test
        void weakPasswordOrInvalidEmailBlocksStartup() {
            assertThatThrownBy(() -> bootstrap("admin@example.com", "debole", null, null))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("password");
            assertThatThrownBy(() -> bootstrap("non-email", "PasswordSicura2026", null, null))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("email");
        }

        @Test
        void emailOfExistingUserIsRefused() {
            users.saveAndFlush(new User("Già", "Registrato", "occupata@example.com", "hash"));

            assertThatThrownBy(() -> bootstrap("occupata@example.com", "PasswordSicura2026", null, null))
                    .isInstanceOf(IllegalStateException.class);
        }

        private Optional<User> bootstrap(String email, String password, String firstName, String lastName) {
            return userService.createFirstAdmin(new BootstrapAdmin(email, password, firstName, lastName));
        }
    }

    @Nested
    class EmailQueue {

        @Autowired
        private EmailNotificationRepository notifications;
        @Autowired
        private UserRepository users;
        @Autowired
        private JsonMapper jsonMapper;
        @Autowired
        private PlatformTransactionManager transactionManager;
        @Autowired
        private AppProperties properties;
        @Autowired
        private EmailTemplateService templates;
        /** Bean Spring (con proxy transazionale), necessario per verificare Propagation.MANDATORY. */
        @Autowired
        private EmailService springEmailService;

        private final MutableClock clock = new MutableClock(Instant.parse("2026-10-19T10:00:00Z"));
        private final List<OutgoingEmail> sent = new ArrayList<>();
        private boolean senderFails;
        private User recipient;
        private EmailService service;

        @BeforeEach
        void setUp() {
            recipient = users.saveAndFlush(new User("Mario", "Rossi", "mario.email@example.com", "hash"));
            EmailSender fakeSender = email -> {
                if (senderFails) {
                    throw new IllegalStateException("provider non raggiungibile");
                }
                sent.add(email);
                return "msg-" + sent.size();
            };
            service = new EmailService(notifications, fakeSender, templates, jsonMapper, transactionManager, clock,
                    properties);
        }

        @Test
        void sameIdempotencyKeyIsQueuedOnlyOnce() {
            assertThat(service.enqueue(message("CHIAVE-1", NotificationType.ACCOUNT_APPROVED))).isTrue();
            assertThat(service.enqueue(message("CHIAVE-1", NotificationType.ACCOUNT_APPROVED))).isFalse();

            assertThat(notifications.count()).isEqualTo(1);
        }

        @Test
        void workerSendsDueEmailsAndRecordsResult() {
            service.enqueue(message("CHIAVE-2", NotificationType.ACCOUNT_APPROVED));

            assertThat(service.processDueEmails()).isEqualTo(1);

            assertThat(sent).singleElement().satisfies(email -> {
                assertThat(email.toEmail()).isEqualTo("mario.email@example.com");
                assertThat(email.subject()).isEqualTo("Oggetto di prova");
                assertThat(email.textBody()).isEqualTo("Testo con link https://example.com/x");
            });
            EmailNotification stored = notifications.findAll().getFirst();
            assertThat(stored.getStatus()).isEqualTo(EmailStatus.SENT);
            assertThat(stored.getProviderMessageId()).isEqualTo("msg-1");
            assertThat(stored.getAttempts()).isEqualTo(1);
            // Già inviata: un secondo giro del worker non la rimanda.
            assertThat(service.processDueEmails()).isZero();
        }

        @Test
        void sensitiveContentIsErasedAfterSending() {
            service.enqueue(message("CHIAVE-3", NotificationType.PASSWORD_RESET));

            service.processDueEmails();

            assertThat(notifications.findAll().getFirst().getPayload()).doesNotContain("https://example.com/x");
        }

        @Test
        void failuresAreRetriedWithGrowingDelayThenMarkedFailed() {
            senderFails = true;
            service.enqueue(message("CHIAVE-4", NotificationType.ACCOUNT_APPROVED));

            assertThat(service.processDueEmails()).isEqualTo(1);
            EmailNotification stored = notifications.findAll().getFirst();
            assertThat(stored.getStatus()).isEqualTo(EmailStatus.PENDING);
            assertThat(stored.getNextAttemptAt()).isEqualTo(clock.instant().plusSeconds(30));
            assertThat(stored.getLastError()).contains("provider non raggiungibile");

            // Prima della scadenza del ritardo non si riprova.
            clock.advance(Duration.ofSeconds(10));
            assertThat(service.processDueEmails()).isZero();

            for (int attempt = 2; attempt <= properties.mail().maxAttempts(); attempt++) {
                clock.advance(Duration.ofHours(1));
                assertThat(service.processDueEmails()).isEqualTo(1);
            }
            assertThat(stored.getStatus()).isEqualTo(EmailStatus.FAILED);
            assertThat(stored.getAttempts()).isEqualTo(properties.mail().maxAttempts());
            clock.advance(Duration.ofHours(1));
            assertThat(service.processDueEmails()).isZero();
        }

        @Test
        void enqueueRequiresAnExistingTransaction() {
            assertThatThrownBy(() -> new TransactionTemplate(transactionManager,
                    new DefaultTransactionDefinition(TransactionDefinition.PROPAGATION_NOT_SUPPORTED))
                    .executeWithoutResult(status -> springEmailService.enqueue(message("CHIAVE-5",
                            NotificationType.ACCOUNT_APPROVED))))
                    .isInstanceOf(IllegalTransactionStateException.class);
        }

        @Test
        void retryDelayGrowsExponentiallyUpToOneHour() {
            assertThat(EmailService.retryDelay(1)).isEqualTo(Duration.ofSeconds(30));
            assertThat(EmailService.retryDelay(2)).isEqualTo(Duration.ofMinutes(1));
            assertThat(EmailService.retryDelay(4)).isEqualTo(Duration.ofMinutes(4));
            assertThat(EmailService.retryDelay(30)).isEqualTo(Duration.ofHours(1));
        }

        private EmailMessage message(String key, NotificationType type) {
            return new EmailMessage(type, recipient, "Oggetto di prova", "Testo con link https://example.com/x",
                    "<p>Testo con link https://example.com/x</p>", "USER", String.valueOf(recipient.getId()), key);
        }
    }
}

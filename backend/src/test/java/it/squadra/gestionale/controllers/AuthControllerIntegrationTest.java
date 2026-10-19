package it.squadra.gestionale.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import it.squadra.gestionale.entities.User;
import it.squadra.gestionale.entities.User.AccountStatus;
import it.squadra.gestionale.entities.User.Role;
import it.squadra.gestionale.exceptions.ApiException;
import it.squadra.gestionale.repositories.RefreshTokenRepository;
import it.squadra.gestionale.repositories.UserRepository;
import it.squadra.gestionale.security.AuthTokens;
import it.squadra.gestionale.security.JwtService;
import it.squadra.gestionale.services.RefreshTokenService;
import it.squadra.gestionale.support.IntegrationTest;
import jakarta.persistence.EntityManager;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Endpoint di autenticazione: registrazione, login, rinnovo, logout, utente corrente e limiti di richieste. */
@IntegrationTest
class AuthControllerIntegrationTest {

    @Nested
    class Registration {

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

    @Nested
    class Login {

        private static final String PASSWORD = "Calcetto2026";
        private static final String GENERIC_ERROR = "Email o password non corretti.";

        @Autowired
        private MockMvc mockMvc;
        @Autowired
        private UserRepository users;
        @Autowired
        private RefreshTokenRepository refreshTokens;
        @Autowired
        private PasswordEncoder passwordEncoder;

        private String passwordHash;

        @BeforeEach
        void hashPasswordOnce() {
            passwordHash = passwordEncoder.encode(PASSWORD);
        }

        @Test
        void successfulLoginReturnsUsableAccessTokenAndHttpOnlyRefreshCookie() throws Exception {
            User user = user("attivo@example.com", AccountStatus.ACTIVE);

            MvcResult result = login("Attivo@Example.com", PASSWORD)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.tokenType").value("Bearer"))
                    .andExpect(jsonPath("$.expiresAt").exists())
                    .andExpect(jsonPath("$.user.email").value("attivo@example.com"))
                    .andExpect(jsonPath("$.refreshToken").doesNotExist())
                    .andReturn();

            String setCookie = result.getResponse().getHeader(HttpHeaders.SET_COOKIE);
            assertThat(setCookie)
                    .startsWith(AuthTokens.COOKIE_NAME + "=")
                    .contains("HttpOnly", "SameSite=Strict", "Path=/api/v1/auth", "Max-Age=");
            String rawRefresh = setCookie.substring(setCookie.indexOf('=') + 1, setCookie.indexOf(';'));
            assertThat(refreshTokens.findByTokenHash(AuthTokens.hash(rawRefresh))).isPresent();

            String accessToken = JsonPath.read(result.getResponse().getContentAsString(), "$.accessToken");
            mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + accessToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(user.getId()));

            assertThat(users.findById(user.getId()).orElseThrow().getLastLoginAt()).isNotNull();
        }

        @Test
        void wrongPasswordAndUnknownEmailGiveTheSameError() throws Exception {
            user("esistente@example.com", AccountStatus.ACTIVE);

            login("esistente@example.com", "PasswordSbagliata1")
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.detail").value(GENERIC_ERROR));
            login("inesistente@example.com", PASSWORD)
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.detail").value(GENERIC_ERROR));
        }

        @Test
        void fiveFailuresLockTheAccountEvenForTheCorrectPassword() throws Exception {
            User user = user("bersaglio@example.com", AccountStatus.ACTIVE);

            for (int i = 0; i < 5; i++) {
                login("bersaglio@example.com", "Tentativo" + i + "xx").andExpect(status().isUnauthorized());
            }

            login("bersaglio@example.com", PASSWORD)
                    .andExpect(status().isLocked())
                    .andExpect(jsonPath("$.code").value("ACCOUNT_LOCKED"));
            assertThat(users.findById(user.getId()).orElseThrow().getLockedUntil()).isAfter(Instant.now());
        }

        @Test
        void expiredLockAllowsLoginAgainAndSuccessResetsCounter() throws Exception {
            User user = user("sbloccato@example.com", AccountStatus.ACTIVE);
            user.setLockedUntil(Instant.now().minusSeconds(1));
            user.setFailedLoginAttempts(3);
            users.saveAndFlush(user);

            login("sbloccato@example.com", PASSWORD).andExpect(status().isOk());

            User reloaded = users.findById(user.getId()).orElseThrow();
            assertThat(reloaded.getFailedLoginAttempts()).isZero();
            assertThat(reloaded.getLockedUntil()).isNull();
        }

        @ParameterizedTest
        @CsvSource({"PENDING, ACCOUNT_PENDING", "REJECTED, ACCOUNT_REJECTED", "SUSPENDED, ACCOUNT_SUSPENDED"})
        void inactiveAccountsGetNoTokens(AccountStatus status, String code) throws Exception {
            user("stato." + status.name().toLowerCase() + "@example.com", status);

            MvcResult result = login("stato." + status.name().toLowerCase() + "@example.com", PASSWORD)
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value(code))
                    .andExpect(jsonPath("$.accessToken").doesNotExist())
                    .andReturn();
            assertThat(result.getResponse().getHeader(HttpHeaders.SET_COOKIE)).isNull();
        }

        @Test
        void inactiveAccountWithWrongPasswordDoesNotRevealItsStatus() throws Exception {
            user("segreto@example.com", AccountStatus.SUSPENDED);

            login("segreto@example.com", "PasswordSbagliata1")
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.detail").value(GENERIC_ERROR));
        }

        @Test
        void missingFieldsAreValidationErrors() throws Exception {
            mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors.email").exists())
                    .andExpect(jsonPath("$.fieldErrors.password").exists());
        }

        private User user(String email, AccountStatus status) {
            User user = new User("Test", "Login", email, passwordHash);
            user.changeStatus(status, null, Instant.now());
            return users.saveAndFlush(user);
        }

        private ResultActions login(String email, String password) throws Exception {
            return mockMvc.perform(post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(HttpHeaders.USER_AGENT, "JUnit")
                    .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password)));
        }
    }

    @Nested
    class RefreshAndLogout {

        private static final String PASSWORD = "Calcetto2026";

        @Autowired
        private MockMvc mockMvc;
        @Autowired
        private UserRepository users;
        @Autowired
        private PasswordEncoder passwordEncoder;

        private User user;

        @BeforeEach
        void createActiveUser() {
            user = new User("Test", "Sessione", "sessione@example.com", passwordEncoder.encode(PASSWORD));
            user.changeStatus(AccountStatus.ACTIVE, null, Instant.now());
            user = users.saveAndFlush(user);
        }

        @Test
        void refreshRotatesCookieAndReturnsNewAccessToken() throws Exception {
            String firstCookie = loginAndGetRefreshCookie();

            MvcResult result = refresh(firstCookie, true)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.accessToken").isNotEmpty())
                    .andExpect(jsonPath("$.user.id").value(user.getId()))
                    .andReturn();

            String secondCookie = cookieValue(result);
            assertThat(secondCookie).isNotBlank().isNotEqualTo(firstCookie);
        }

        @Test
        void reusingOldCookieRevokesSessionAndClearsCookie() throws Exception {
            String firstCookie = loginAndGetRefreshCookie();
            String secondCookie = cookieValue(refresh(firstCookie, true).andExpect(status().isOk()).andReturn());

            MvcResult reuse = refresh(firstCookie, true).andExpect(status().isUnauthorized()).andReturn();
            assertThat(reuse.getResponse().getHeader(HttpHeaders.SET_COOKIE)).contains("Max-Age=0");

            // Anche il token legittimo più recente è stato revocato: la sessione è compromessa.
            refresh(secondCookie, true).andExpect(status().isUnauthorized());
        }

        @Test
        void refreshWithoutCustomHeaderIsForbidden() throws Exception {
            String cookie = loginAndGetRefreshCookie();

            refresh(cookie, false)
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        }

        @Test
        void refreshWithoutCookieIsUnauthorized() throws Exception {
            mockMvc.perform(post("/api/v1/auth/refresh").header(AuthTokens.CSRF_HEADER, AuthTokens.CSRF_VALUE))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        void suspendedUserCannotRefresh() throws Exception {
            String cookie = loginAndGetRefreshCookie();
            user.changeStatus(AccountStatus.SUSPENDED, null, Instant.now());
            users.saveAndFlush(user);

            refresh(cookie, true).andExpect(status().isUnauthorized());
        }

        @Test
        void logoutRevokesSessionAndClearsCookie() throws Exception {
            String cookie = loginAndGetRefreshCookie();

            MvcResult result = mockMvc.perform(withCsrfHeader(post("/api/v1/auth/logout"))
                            .cookie(new Cookie(AuthTokens.COOKIE_NAME, cookie)))
                    .andExpect(status().isNoContent())
                    .andReturn();

            assertThat(result.getResponse().getHeader(HttpHeaders.SET_COOKIE)).contains("Max-Age=0");
            refresh(cookie, true).andExpect(status().isUnauthorized());
        }

        @Test
        void logoutWithoutCookieIsIdempotent() throws Exception {
            mockMvc.perform(withCsrfHeader(post("/api/v1/auth/logout"))).andExpect(status().isNoContent());
        }

        @Test
        void logoutWithoutCustomHeaderIsForbidden() throws Exception {
            String cookie = loginAndGetRefreshCookie();

            mockMvc.perform(post("/api/v1/auth/logout").cookie(new Cookie(AuthTokens.COOKIE_NAME, cookie)))
                    .andExpect(status().isForbidden());
            refresh(cookie, true).andExpect(status().isOk());
        }

        private String loginAndGetRefreshCookie() throws Exception {
            MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"sessione@example.com\",\"password\":\"%s\"}".formatted(PASSWORD)))
                    .andExpect(status().isOk())
                    .andReturn();
            assertThat((String) JsonPath.read(result.getResponse().getContentAsString(), "$.accessToken")).isNotBlank();
            return cookieValue(result);
        }

        private ResultActions refresh(String cookie, boolean withHeader) throws Exception {
            MockHttpServletRequestBuilder request = post("/api/v1/auth/refresh")
                    .cookie(new Cookie(AuthTokens.COOKIE_NAME, cookie));
            return mockMvc.perform(withHeader ? withCsrfHeader(request) : request);
        }

        private static MockHttpServletRequestBuilder withCsrfHeader(MockHttpServletRequestBuilder request) {
            return request.header(AuthTokens.CSRF_HEADER, AuthTokens.CSRF_VALUE);
        }

        private static String cookieValue(MvcResult result) {
            String setCookie = result.getResponse().getHeader(HttpHeaders.SET_COOKIE);
            assertThat(setCookie).startsWith(AuthTokens.COOKIE_NAME + "=");
            return setCookie.substring(setCookie.indexOf('=') + 1, setCookie.indexOf(';'));
        }
    }

    @Import(CurrentUserAndRoles.RoleProbeController.class)
    @Nested
    class CurrentUserAndRoles {

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

    @TestPropertySource(properties = {
            "app.rate-limit.enabled=true",
            "app.rate-limit.rules[0].path=/api/v1/auth/login",
            "app.rate-limit.rules[0].capacity=3",
            "app.rate-limit.rules[0].window=PT1M"
    })
    @Nested
    class RateLimit {

        @Autowired
        private MockMvc mockMvc;

        @Test
        void loginIsLimitedPerIpWith429AndRetryAfter() throws Exception {
            for (int i = 0; i < 3; i++) {
                login("10.0.0.1").andExpect(status().isUnauthorized());
            }

            login("10.0.0.1")
                    .andExpect(status().isTooManyRequests())
                    .andExpect(header().exists("Retry-After"))
                    .andExpect(jsonPath("$.code").value("TOO_MANY_REQUESTS"));

            // Un altro IP non è influenzato.
            login("10.0.0.2").andExpect(status().isUnauthorized());
            // Gli endpoint senza regola e le GET non sono limitati.
            mockMvc.perform(get("/api/v1/public/info").with(request -> remote(request, "10.0.0.1")))
                    .andExpect(status().isOk());
        }

        private ResultActions login(String ip) throws Exception {
            return mockMvc.perform(post("/api/v1/auth/login")
                    .with(request -> remote(request, ip))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"email\":\"nessuno@example.com\",\"password\":\"Sbagliata123\"}"));
        }

        private static MockHttpServletRequest remote(MockHttpServletRequest request, String ip) {
            request.setRemoteAddr(ip);
            return request;
        }
    }

    @Nested
    class PasswordReset {

        private static final String OLD_PASSWORD = "VecchiaPassword1";
        private static final String NEW_PASSWORD = "NuovaPassword2026";
        private static final Pattern TOKEN_IN_LINK = Pattern.compile("token=([A-Za-z0-9_-]+)");

        @Autowired
        private MockMvc mockMvc;
        @Autowired
        private UserRepository users;
        @Autowired
        private PasswordEncoder passwordEncoder;
        @Autowired
        private RefreshTokenService refreshTokenService;
        @Autowired
        private JdbcTemplate jdbc;
        @Autowired
        private EntityManager entityManager;

        @Test
        void unknownEmailGetsSameAnswerAndNoEmail() throws Exception {
            forgot("nessuno@example.com")
                    .andExpect(status().isAccepted())
                    .andExpect(jsonPath("$.message").value(AuthController.FORGOT_PASSWORD_MESSAGE));

            assertThat(jdbc.queryForObject("select count(*) from email_notifications", Integer.class)).isZero();
        }

        @Test
        void knownEmailQueuesOneEmailWithSingleUseLinkAndStoresOnlyTheHash() throws Exception {
            User user = user("reset@example.com", AccountStatus.ACTIVE);

            forgot("Reset@Example.com")
                    .andExpect(status().isAccepted())
                    .andExpect(jsonPath("$.message").value(AuthController.FORGOT_PASSWORD_MESSAGE));

            String token = tokenFromEmail(user);
            assertThat(jdbc.queryForObject("select token_hash from user_tokens where user_id = ?", String.class,
                    user.getId())).isEqualTo(AuthTokens.hash(token)).isNotEqualTo(token);
            // Una seconda richiesta ravvicinata non genera altre email.
            forgot("reset@example.com").andExpect(status().isAccepted());
            assertThat(jdbc.queryForObject("select count(*) from email_notifications", Integer.class)).isEqualTo(1);
        }

        @Test
        void suspendedAccountReceivesNoEmail() throws Exception {
            user("sospeso.reset@example.com", AccountStatus.SUSPENDED);

            forgot("sospeso.reset@example.com").andExpect(status().isAccepted());

            assertThat(jdbc.queryForObject("select count(*) from email_notifications", Integer.class)).isZero();
        }

        @Test
        void resetChangesPasswordClosesSessionsAndUnlocksAccount() throws Exception {
            User user = user("completo@example.com", AccountStatus.ACTIVE);
            user.setLockedUntil(Instant.now().plusSeconds(600));
            user.setFailedLoginAttempts(3);
            users.saveAndFlush(user);
            String session = refreshTokenService.createSession(user, "JUnit", "127.0.0.1").value();
            forgot("completo@example.com");
            String token = tokenFromEmail(user);

            reset(token, NEW_PASSWORD)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").exists());

            User reloaded = users.findById(user.getId()).orElseThrow();
            assertThat(passwordEncoder.matches(NEW_PASSWORD, reloaded.getPasswordHash())).isTrue();
            assertThat(passwordEncoder.matches(OLD_PASSWORD, reloaded.getPasswordHash())).isFalse();
            assertThat(reloaded.getLockedUntil()).isNull();
            assertThat(reloaded.getFailedLoginAttempts()).isZero();
            assertThat(reloaded.getEmailVerifiedAt()).isNotNull();
            assertThatThrownBy(() -> refreshTokenService.rotate(session, "JUnit", "127.0.0.1"))
                    .isInstanceOf(ApiException.class);
            assertThat(jdbc.queryForObject("select count(*) from audit_logs where action = 'PASSWORD_RESET'",
                    Integer.class)).isEqualTo(1);
            login("completo@example.com", NEW_PASSWORD).andExpect(status().isOk());
        }

        @Test
        void linkWorksOnlyOnce() throws Exception {
            User user = user("una.volta@example.com", AccountStatus.ACTIVE);
            forgot("una.volta@example.com");
            String token = tokenFromEmail(user);

            reset(token, NEW_PASSWORD).andExpect(status().isOk());
            reset(token, "AltraPassword99")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
        }

        @Test
        void expiredOrUnknownLinkIsRejected() throws Exception {
            User user = user("scaduto@example.com", AccountStatus.ACTIVE);
            forgot("scaduto@example.com");
            String token = tokenFromEmail(user);
            jdbc.update("update user_tokens set created_at = now() - interval '2 hours', "
                    + "expires_at = now() - interval '1 hour' where user_id = ?", user.getId());
            // Lo stesso test gira in una sola transazione: si scarta la copia del token già in memoria.
            entityManager.clear();

            reset(token, NEW_PASSWORD).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
            reset("token-inventato", NEW_PASSWORD).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
        }

        @Test
        void weakNewPasswordIsValidationError() throws Exception {
            reset("qualsiasi", "corta")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors.newPassword").exists());
        }

        private User user(String email, AccountStatus status) {
            User user = new User("Giulia", "Reset", email, passwordEncoder.encode(OLD_PASSWORD));
            user.changeStatus(status, null, Instant.now());
            return users.saveAndFlush(user);
        }

        private String tokenFromEmail(User user) {
            String text = jdbc.queryForObject("select payload->>'text' from email_notifications "
                    + "where notification_type = 'PASSWORD_RESET' and recipient_user_id = ?", String.class, user.getId());
            Matcher matcher = TOKEN_IN_LINK.matcher(text);
            assertThat(matcher.find()).isTrue();
            return matcher.group(1);
        }

        private ResultActions forgot(String email) throws Exception {
            return mockMvc.perform(post("/api/v1/auth/forgot-password").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"email\":\"%s\"}".formatted(email)));
        }

        private ResultActions reset(String token, String newPassword) throws Exception {
            return mockMvc.perform(post("/api/v1/auth/reset-password").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"token\":\"%s\",\"newPassword\":\"%s\"}".formatted(token, newPassword)));
        }

        private ResultActions login(String email, String password) throws Exception {
            return mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password)));
        }
    }

    @Nested
    class EmailVerification {

        private static final Pattern TOKEN_IN_LINK = Pattern.compile("token=([A-Za-z0-9_-]+)");

        @Autowired
        private MockMvc mockMvc;
        @Autowired
        private EntityManager entityManager;
        @Autowired
        private UserRepository users;
        @Autowired
        private JdbcTemplate jdbc;

        @Test
        void registrationSendsVerificationLinkAndLinkConfirmsEmailOnce() throws Exception {
            register("verifica@example.com");
            User user = users.findByEmail("verifica@example.com").orElseThrow();
            assertThat(user.getEmailVerifiedAt()).isNull();
            String token = tokenFromEmail(user);
            assertThat(lastEmailText(user)).contains("/verify-email?token=");

            post("/api/v1/auth/verify-email", "{\"token\":\"%s\"}".formatted(token))
                    .andExpect(status().isOk());

            assertThat(users.findById(user.getId()).orElseThrow().getEmailVerifiedAt()).isNotNull();
            assertThat(jdbc.queryForObject("select count(*) from audit_logs where action = 'EMAIL_VERIFIED'",
                    Integer.class)).isEqualTo(1);
            post("/api/v1/auth/verify-email", "{\"token\":\"%s\"}".formatted(token))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
        }

        @Test
        void resendAnswersAlwaysTheSameAndRespectsCooldown() throws Exception {
            register("reinvio@example.com");
            User user = users.findByEmail("reinvio@example.com").orElseThrow();

            post("/api/v1/auth/resend-verification", "{\"email\":\"reinvio@example.com\"}")
                    .andExpect(status().isAccepted())
                    .andExpect(jsonPath("$.message").value(AuthController.RESEND_VERIFICATION_MESSAGE));
            post("/api/v1/auth/resend-verification", "{\"email\":\"sconosciuto@example.com\"}")
                    .andExpect(status().isAccepted())
                    .andExpect(jsonPath("$.message").value(AuthController.RESEND_VERIFICATION_MESSAGE));

            // La registrazione ha appena inviato un link: il reinvio immediato non genera altre email.
            assertThat(jdbc.queryForObject("select count(*) from email_notifications where recipient_user_id = ?",
                    Integer.class, user.getId())).isEqualTo(1);
        }

        @Test
        void newLinkInvalidatesThePreviousOne() throws Exception {
            register("doppio.link@example.com");
            User user = users.findByEmail("doppio.link@example.com").orElseThrow();
            String firstToken = tokenFromEmail(user);
            // Si simula il passare del tempo oltre l'intervallo minimo tra due invii.
            jdbc.update("update user_tokens set created_at = created_at - interval '10 minutes' where user_id = ?",
                    user.getId());

            post("/api/v1/auth/resend-verification", "{\"email\":\"doppio.link@example.com\"}")
                    .andExpect(status().isAccepted());
            // Ogni richiesta HTTP reale ha il proprio contesto di persistenza: qui si simula scartando la cache.
            entityManager.clear();

            post("/api/v1/auth/verify-email", "{\"token\":\"%s\"}".formatted(firstToken))
                    .andExpect(status().isBadRequest());
            post("/api/v1/auth/verify-email", "{\"token\":\"%s\"}".formatted(tokenFromEmail(user)))
                    .andExpect(status().isOk());
        }

        private void register(String email) throws Exception {
            post("/api/v1/auth/register", """
                    {"firstName":"Vera","lastName":"Verifica","email":"%s","password":"Calcetto2026"}"""
                    .formatted(email)).andExpect(status().isAccepted());
        }

        private String lastEmailText(User user) {
            return jdbc.queryForObject("select payload->>'text' from email_notifications "
                    + "where notification_type = 'EMAIL_VERIFICATION' and recipient_user_id = ? "
                    + "order by id desc limit 1", String.class, user.getId());
        }

        private String tokenFromEmail(User user) {
            Matcher matcher = TOKEN_IN_LINK.matcher(lastEmailText(user));
            assertThat(matcher.find()).isTrue();
            return matcher.group(1);
        }

        private ResultActions post(String path, String json) throws Exception {
            return mockMvc.perform(MockMvcRequestBuilders.post(path).contentType(MediaType.APPLICATION_JSON)
                    .content(json));
        }
    }

    @Nested
    class ChangePassword {

        @Autowired
        private MockMvc mockMvc;
        @Autowired
        private UserRepository users;
        @Autowired
        private PasswordEncoder passwordEncoder;
        @Autowired
        private JwtService jwtService;
        @Autowired
        private RefreshTokenService refreshTokenService;

        private User user;

        @BeforeEach
        void createUser() {
            user = new User("Carla", "Cambio", "cambio@example.com", passwordEncoder.encode("VecchiaPassword1"));
            user.changeStatus(AccountStatus.ACTIVE, null, Instant.now());
            user = users.saveAndFlush(user);
        }

        @Test
        void changeClosesOtherSessionsAndOpensANewOne() throws Exception {
            String otherDevice = refreshTokenService.createSession(user, "Altro dispositivo", "10.0.0.9").value();

            MvcResult result = change("VecchiaPassword1", "NuovaPassword2026")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.accessToken").isNotEmpty())
                    .andReturn();

            assertThat(result.getResponse().getHeader(HttpHeaders.SET_COOKIE)).startsWith(AuthTokens.COOKIE_NAME + "=");
            assertThat(passwordEncoder.matches("NuovaPassword2026",
                    users.findById(user.getId()).orElseThrow().getPasswordHash())).isTrue();
            assertThatThrownBy(() -> refreshTokenService.rotate(otherDevice, "Altro dispositivo", "10.0.0.9"))
                    .isInstanceOf(ApiException.class);
        }

        @Test
        void wrongCurrentPasswordIsRejected() throws Exception {
            change("Sbagliata12345", "NuovaPassword2026")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.detail").value("La password attuale non è corretta."));
        }

        @Test
        void requiresAuthentication() throws Exception {
            mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/auth/change-password")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"currentPassword\":\"VecchiaPassword1\",\"newPassword\":\"NuovaPassword2026\"}"))
                    .andExpect(status().isUnauthorized());
        }

        private ResultActions change(String current, String next) throws Exception {
            return mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/auth/change-password")
                    .header("Authorization", "Bearer " + jwtService.issueAccessToken(user).value())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"currentPassword\":\"%s\",\"newPassword\":\"%s\"}".formatted(current, next)));
        }
    }
}

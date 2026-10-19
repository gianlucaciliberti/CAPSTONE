package it.squadra.gestionale.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import it.squadra.gestionale.entities.User;
import it.squadra.gestionale.entities.User.AccountStatus;
import it.squadra.gestionale.entities.User.Role;
import it.squadra.gestionale.repositories.RefreshTokenRepository;
import it.squadra.gestionale.repositories.UserRepository;
import it.squadra.gestionale.security.AuthTokens;
import it.squadra.gestionale.security.JwtService;
import it.squadra.gestionale.support.IntegrationTest;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
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
}

package it.squadra.gestionale.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import it.squadra.gestionale.entities.User;
import it.squadra.gestionale.enums.AccountStatus;
import it.squadra.gestionale.repositories.RefreshTokenRepository;
import it.squadra.gestionale.repositories.UserRepository;
import it.squadra.gestionale.security.OpaqueTokens;
import it.squadra.gestionale.security.RefreshTokenCookies;
import it.squadra.gestionale.support.IntegrationTest;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

@IntegrationTest
class LoginIntegrationTest {

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
                .startsWith(RefreshTokenCookies.COOKIE_NAME + "=")
                .contains("HttpOnly", "SameSite=Strict", "Path=/api/v1/auth", "Max-Age=");
        String rawRefresh = setCookie.substring(setCookie.indexOf('=') + 1, setCookie.indexOf(';'));
        assertThat(refreshTokens.findByTokenHash(OpaqueTokens.hash(rawRefresh))).isPresent();

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

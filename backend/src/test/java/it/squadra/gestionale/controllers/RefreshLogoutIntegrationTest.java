package it.squadra.gestionale.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import it.squadra.gestionale.entities.User;
import it.squadra.gestionale.enums.AccountStatus;
import it.squadra.gestionale.repositories.UserRepository;
import it.squadra.gestionale.security.CsrfProtection;
import it.squadra.gestionale.security.RefreshTokenCookies;
import it.squadra.gestionale.support.IntegrationTest;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@IntegrationTest
class RefreshLogoutIntegrationTest {

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
        mockMvc.perform(post("/api/v1/auth/refresh").header(CsrfProtection.HEADER, CsrfProtection.EXPECTED_VALUE))
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
                        .cookie(new Cookie(RefreshTokenCookies.COOKIE_NAME, cookie)))
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

        mockMvc.perform(post("/api/v1/auth/logout").cookie(new Cookie(RefreshTokenCookies.COOKIE_NAME, cookie)))
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
                .cookie(new Cookie(RefreshTokenCookies.COOKIE_NAME, cookie));
        return mockMvc.perform(withHeader ? withCsrfHeader(request) : request);
    }

    private static MockHttpServletRequestBuilder withCsrfHeader(MockHttpServletRequestBuilder request) {
        return request.header(CsrfProtection.HEADER, CsrfProtection.EXPECTED_VALUE);
    }

    private static String cookieValue(MvcResult result) {
        String setCookie = result.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        assertThat(setCookie).startsWith(RefreshTokenCookies.COOKIE_NAME + "=");
        return setCookie.substring(setCookie.indexOf('=') + 1, setCookie.indexOf(';'));
    }
}

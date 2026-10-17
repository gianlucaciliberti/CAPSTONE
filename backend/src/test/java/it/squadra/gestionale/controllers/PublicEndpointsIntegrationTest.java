package it.squadra.gestionale.controllers;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import it.squadra.gestionale.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@IntegrationTest
class PublicEndpointsIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void healthCheckIsPublicAndUp() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void publicInfoReportsRomeTimezone() throws Exception {
        mockMvc.perform(get("/api/v1/public/info"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.timezone").value("Europe/Rome"))
                .andExpect(jsonPath("$.serverTime").exists());
    }

    @Test
    void protectedEndpointRequiresAuthenticationWithProblemDetail() throws Exception {
        mockMvc.perform(get("/api/v1/matches"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void corsAllowsOnlyConfiguredOrigin() throws Exception {
        mockMvc.perform(get("/api/v1/public/info").header("Origin", "http://localhost:5173"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));

        mockMvc.perform(get("/api/v1/public/info").header("Origin", "https://sito-malevolo.example"))
                .andExpect(status().isForbidden());
    }
}

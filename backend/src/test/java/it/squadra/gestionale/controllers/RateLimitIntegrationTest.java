package it.squadra.gestionale.controllers;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import it.squadra.gestionale.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@IntegrationTest
@TestPropertySource(properties = {
        "app.rate-limit.enabled=true",
        "app.rate-limit.rules[0].path=/api/v1/auth/login",
        "app.rate-limit.rules[0].capacity=3",
        "app.rate-limit.rules[0].window=PT1M"
})
class RateLimitIntegrationTest {

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

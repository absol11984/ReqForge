package com.apishield.controller;

import com.apishield.dto.RateLimitResult;
import com.apishield.entity.Client;
import com.apishield.entity.ClientStatus;
import com.apishield.filter.RateLimitFilter;
import com.apishield.service.ClientService;
import com.apishield.service.RateLimitService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest({DemoController.class, ClientController.class})
@Import(RateLimitFilter.class)
class RateLimitFilterTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ClientService clientService;

    @MockBean
    private RateLimitService rateLimitService;

    private static final String DEMO_PATH = "/api/demo/products";
    private static final String CLIENTS_PATH = "/api/clients";

    // ── 1. Valid API key reaches controller ─────────────────────────────
    @Test
    void validApiKey_reachesController_returns200() throws Exception {
        String apiKey = "ask_live_validkey1234567890";
        Client client = new Client(UUID.randomUUID(), "client-app", apiKey, ClientStatus.ACTIVE, 100, 60);
        when(clientService.getClientEntityByApiKey(eq(apiKey))).thenReturn(client);
        when(rateLimitService.checkRateLimit(eq(apiKey), eq(100), eq(60)))
                .thenReturn(new RateLimitResult(true, 100, 99, 55));

        mockMvc.perform(get(DEMO_PATH).header("X-API-Key", apiKey))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)));
    }

    // ── 2. Missing API key → 401 ────────────────────────────────────────
    @Test
    void missingApiKey_returns401() throws Exception {
        mockMvc.perform(get(DEMO_PATH))
                .andDo(print())
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status", is(401)))
                .andExpect(jsonPath("$.error", is("Unauthorized")))
                .andExpect(jsonPath("$.message", is("Missing X-API-Key header")));
    }

    // ── 3. Invalid API key → 401 ────────────────────────────────────────
    @Test
    void invalidApiKey_returns401() throws Exception {
        String apiKey = "ask_live_unknownkey";
        when(clientService.getClientEntityByApiKey(eq(apiKey))).thenReturn(null);

        mockMvc.perform(get(DEMO_PATH).header("X-API-Key", apiKey))
                .andDo(print())
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status", is(401)))
                .andExpect(jsonPath("$.error", is("Unauthorized")))
                .andExpect(jsonPath("$.message", is("Invalid or unknown API key")));
    }

    // ── 4. Inactive client → 403 ────────────────────────────────────────
    @Test
    void inactiveClient_returns403() throws Exception {
        String apiKey = "ask_live_inactivekey";
        Client client = new Client(UUID.randomUUID(), "inactive-app", apiKey, ClientStatus.INACTIVE, 100, 60);
        when(clientService.getClientEntityByApiKey(eq(apiKey))).thenReturn(client);

        mockMvc.perform(get(DEMO_PATH).header("X-API-Key", apiKey))
                .andDo(print())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status", is(403)))
                .andExpect(jsonPath("$.error", is("Forbidden")))
                .andExpect(jsonPath("$.message", is("Client is inactive")));
    }

    // ── 5. Under limit → reaches controller ─────────────────────────────
    @Test
    void underLimit_reachesController_returns200() throws Exception {
        String apiKey = "ask_live_underlimit";
        Client client = new Client(UUID.randomUUID(), "client-app", apiKey, ClientStatus.ACTIVE, 100, 60);
        when(clientService.getClientEntityByApiKey(eq(apiKey))).thenReturn(client);
        when(rateLimitService.checkRateLimit(eq(apiKey), eq(100), eq(60)))
                .thenReturn(new RateLimitResult(true, 100, 50, 55));

        mockMvc.perform(get(DEMO_PATH).header("X-API-Key", apiKey))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)));
    }

    // ── 6. Over limit → 429 ─────────────────────────────────────────────
    @Test
    void overLimit_returns429() throws Exception {
        String apiKey = "ask_live_overlimit";
        Client client = new Client(UUID.randomUUID(), "client-app", apiKey, ClientStatus.ACTIVE, 5, 60);
        when(clientService.getClientEntityByApiKey(eq(apiKey))).thenReturn(client);
        when(rateLimitService.checkRateLimit(eq(apiKey), eq(5), eq(60)))
                .thenReturn(new RateLimitResult(false, 5, 0, 30));

        mockMvc.perform(get(DEMO_PATH).header("X-API-Key", apiKey))
                .andDo(print())
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.status", is(429)))
                .andExpect(jsonPath("$.error", is("Too Many Requests")))
                .andExpect(jsonPath("$.message", is("Rate limit exceeded")));
    }

    // ── 7. Rate-limit headers present on success ────────────────────────
    @Test
    void rateLimitHeadersPresentOnSuccess() throws Exception {
        String apiKey = "ask_live_withheaders";
        Client client = new Client(UUID.randomUUID(), "client-app", apiKey, ClientStatus.ACTIVE, 10, 60);
        when(clientService.getClientEntityByApiKey(eq(apiKey))).thenReturn(client);
        when(rateLimitService.checkRateLimit(eq(apiKey), eq(10), eq(60)))
                .thenReturn(new RateLimitResult(true, 10, 7, 45));

        mockMvc.perform(get(DEMO_PATH).header("X-API-Key", apiKey))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(header().string("X-RateLimit-Limit", "10"))
                .andExpect(header().string("X-RateLimit-Remaining", "7"))
                .andExpect(header().string("X-RateLimit-Reset", "45"));
    }

    // ── 8. Retry-After header on 429 ────────────────────────────────────
    @Test
    void retryAfterHeaderOn429() throws Exception {
        String apiKey = "ask_live_retryafter";
        Client client = new Client(UUID.randomUUID(), "client-app", apiKey, ClientStatus.ACTIVE, 5, 60);
        when(clientService.getClientEntityByApiKey(eq(apiKey))).thenReturn(client);
        when(rateLimitService.checkRateLimit(eq(apiKey), eq(5), eq(60)))
                .thenReturn(new RateLimitResult(false, 5, 0, 42));

        mockMvc.perform(get(DEMO_PATH).header("X-API-Key", apiKey))
                .andDo(print())
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "42"));
    }

    // ── 9. /api/clients not rate limited ────────────────────────────────
    @Test
    void clientEndpointsNotRateLimited() throws Exception {
        mockMvc.perform(get(CLIENTS_PATH))
                .andDo(print())
                .andExpect(status().isOk());
    }

    // ── 10. Swagger endpoints excluded ──────────────────────────────────
    @Test
    void swaggerEndpointsExcluded() throws Exception {
        mockMvc.perform(get("/swagger-ui.html"))
                .andDo(print())
                .andExpect(status().isNotFound()); // not 401
    }

    // ── 11. Controller no longer performs its own rate-limit check ──────
    @Test
    void controllerDoesNotPerformOwnRateLimitCheck() throws Exception {
        String apiKey = "ask_live_validkey1234567890";
        Client client = new Client(UUID.randomUUID(), "client-app", apiKey, ClientStatus.ACTIVE, 100, 60);
        when(clientService.getClientEntityByApiKey(eq(apiKey))).thenReturn(client);
        when(rateLimitService.checkRateLimit(eq(apiKey), eq(100), eq(60)))
                .thenReturn(new RateLimitResult(true, 100, 99, 55));

        mockMvc.perform(get(DEMO_PATH).header("X-API-Key", apiKey))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)));
    }

    // ── 12. Filter runs exactly once per request ────────────────────────
    @Test
    void filterRunsOncePerRequest() throws Exception {
        String apiKey = "ask_live_once";
        Client client = new Client(UUID.randomUUID(), "client-app", apiKey, ClientStatus.ACTIVE, 100, 60);
        when(clientService.getClientEntityByApiKey(eq(apiKey))).thenReturn(client);
        when(rateLimitService.checkRateLimit(eq(apiKey), eq(100), eq(60)))
                .thenReturn(new RateLimitResult(true, 100, 99, 55));

        mockMvc.perform(get(DEMO_PATH).header("X-API-Key", apiKey))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(header().string("X-RateLimit-Remaining", "99"));
    }
}

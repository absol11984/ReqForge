package com.apishield.controller;

import com.apishield.dto.RateLimitResult;
import com.apishield.entity.Client;
import com.apishield.entity.ClientStatus;
import com.apishield.entity.RateLimitAlgorithm;
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

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
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

    @Test
    void missingApiKey_returns401() throws Exception {
        mockMvc.perform(get(DEMO_PATH))
                .andDo(print())
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status", is(401)))
                .andExpect(jsonPath("$.error", is("Unauthorized")))
                .andExpect(jsonPath("$.message", is("Missing X-API-Key header")));
    }

    @Test
    void invalidApiKey_returns401() throws Exception {
        String apiKey = "ask_live_unknownkey";
        when(clientService.getClientEntityByApiKey(eq(apiKey))).thenReturn(null);

        mockMvc.perform(get(DEMO_PATH).header("X-API-Key", apiKey))
                .andDo(print())
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status", is(401)))
                .andExpect(jsonPath("$.error", is("Unauthorized")));
    }

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

    @Test
    void fixedWindow_allowed_returns200_andHeaders() throws Exception {
        String apiKey = "ask_live_fixed_ok";
        Client client = new Client(UUID.randomUUID(), "client-app", apiKey, ClientStatus.ACTIVE, 10, 60);

        when(clientService.getClientEntityByApiKey(eq(apiKey))).thenReturn(client);
        when(rateLimitService.checkRateLimit(eq(apiKey), eq(10), eq(60), eq(RateLimitAlgorithm.FIXED_WINDOW)))
                .thenReturn(new RateLimitResult(true, 10, 7, 45));

        mockMvc.perform(get(DEMO_PATH).header("X-API-Key", apiKey))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(header().string("X-RateLimit-Limit", "10"))
                .andExpect(header().string("X-RateLimit-Remaining", "7"))
                .andExpect(header().string("X-RateLimit-Reset", "45"));
    }

    @Test
    void fixedWindow_rejected_returns429_andRetryAfter() throws Exception {
        String apiKey = "ask_live_fixed_over";
        Client client = new Client(UUID.randomUUID(), "client-app", apiKey, ClientStatus.ACTIVE, 5, 60);

        when(clientService.getClientEntityByApiKey(eq(apiKey))).thenReturn(client);
        when(rateLimitService.checkRateLimit(eq(apiKey), eq(5), eq(60), eq(RateLimitAlgorithm.FIXED_WINDOW)))
                .thenReturn(new RateLimitResult(false, 5, 0, 42));

        mockMvc.perform(get(DEMO_PATH).header("X-API-Key", apiKey))
                .andDo(print())
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.status", is(429)))
                .andExpect(jsonPath("$.error", is("Too Many Requests")))
                .andExpect(jsonPath("$.message", is("Rate limit exceeded")))
                .andExpect(header().string("X-RateLimit-Limit", "5"))
                .andExpect(header().string("X-RateLimit-Remaining", "0"))
                .andExpect(header().string("X-RateLimit-Reset", "42"))
                .andExpect(header().string("Retry-After", "42"));
    }

    @Test
    void slidingWindow_allowed_returns200_andHeaders() throws Exception {
        String apiKey = "ask_live_sliding_ok";
        Client client = new Client(UUID.randomUUID(), "client-app", apiKey, ClientStatus.ACTIVE, 10, 60, RateLimitAlgorithm.SLIDING_WINDOW);

        when(clientService.getClientEntityByApiKey(eq(apiKey))).thenReturn(client);
        when(rateLimitService.checkRateLimit(eq(apiKey), eq(10), eq(60), eq(RateLimitAlgorithm.SLIDING_WINDOW)))
                .thenReturn(new RateLimitResult(true, 10, 4, 33));

        mockMvc.perform(get(DEMO_PATH).header("X-API-Key", apiKey))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(header().string("X-RateLimit-Remaining", "4"))
                .andExpect(header().string("X-RateLimit-Reset", "33"));
    }

    @Test
    void slidingWindow_rejected_returns429_andRetryAfter() throws Exception {
        String apiKey = "ask_live_sliding_over";
        Client client = new Client(UUID.randomUUID(), "client-app", apiKey, ClientStatus.ACTIVE, 3, 60, RateLimitAlgorithm.SLIDING_WINDOW);

        when(clientService.getClientEntityByApiKey(eq(apiKey))).thenReturn(client);
        when(rateLimitService.checkRateLimit(eq(apiKey), eq(3), eq(60), eq(RateLimitAlgorithm.SLIDING_WINDOW)))
                .thenReturn(new RateLimitResult(false, 3, 0, 12));

        mockMvc.perform(get(DEMO_PATH).header("X-API-Key", apiKey))
                .andDo(print())
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "12"));
    }

    @Test
    void tokenBucket_allowed_returns200_andHeaders() throws Exception {
        String apiKey = "ask_live_bucket_ok";
        Client client = new Client(UUID.randomUUID(), "client-app", apiKey, ClientStatus.ACTIVE, 2, 60, RateLimitAlgorithm.TOKEN_BUCKET);

        when(clientService.getClientEntityByApiKey(eq(apiKey))).thenReturn(client);
        when(rateLimitService.checkRateLimit(eq(apiKey), eq(2), eq(60), eq(RateLimitAlgorithm.TOKEN_BUCKET)))
                .thenReturn(new RateLimitResult(true, 2, 1, 0));

        mockMvc.perform(get(DEMO_PATH).header("X-API-Key", apiKey))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(header().string("X-RateLimit-Remaining", "1"))
                .andExpect(header().string("X-RateLimit-Reset", "0"));
    }

    @Test
    void tokenBucket_rejected_returns429_andRetryAfter() throws Exception {
        String apiKey = "ask_live_bucket_over";
        Client client = new Client(UUID.randomUUID(), "client-app", apiKey, ClientStatus.ACTIVE, 2, 60, RateLimitAlgorithm.TOKEN_BUCKET);

        when(clientService.getClientEntityByApiKey(eq(apiKey))).thenReturn(client);
        when(rateLimitService.checkRateLimit(eq(apiKey), eq(2), eq(60), eq(RateLimitAlgorithm.TOKEN_BUCKET)))
                .thenReturn(new RateLimitResult(false, 2, 0, 8));

        mockMvc.perform(get(DEMO_PATH).header("X-API-Key", apiKey))
                .andDo(print())
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "8"));
    }

    @Test
    void clientEndpointsNotRateLimited() throws Exception {
        mockMvc.perform(get(CLIENTS_PATH))
                .andDo(print())
                .andExpect(status().isOk());
    }

    @Test
    void swaggerEndpointsExcluded() throws Exception {
        mockMvc.perform(get("/swagger-ui.html"))
                .andDo(print())
                .andExpect(status().isNotFound());
    }
}

package com.apishield.controller;

import com.apishield.dto.RateLimitResult;
import com.apishield.entity.Client;
import com.apishield.entity.ClientStatus;
import com.apishield.service.ClientService;
import com.apishield.service.RateLimitService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(DemoController.class)
class DemoControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ClientService clientService;

    @MockBean
    private RateLimitService rateLimitService;

    @Test
    void getProducts_validApiKey_underLimit_returnsProducts() throws Exception {
        String apiKey = "ask_live_validkey1234567890";
        Client client = new Client(UUID.randomUUID(), "client-app", apiKey, ClientStatus.ACTIVE, 100, 60);
        when(clientService.getClientEntityByApiKey(eq(apiKey))).thenReturn(client);
        when(rateLimitService.checkRateLimit(eq(apiKey), eq(100), eq(60)))
                .thenReturn(new RateLimitResult(true, 100, 99, 55));

        mockMvc.perform(get("/api/demo/products")
                        .header("X-API-Key", apiKey))
                .andExpect(status().isOk())
                .andExpect(header().string("X-RateLimit-Limit", "100"))
                .andExpect(header().string("X-RateLimit-Remaining", "99"))
                .andExpect(header().string("X-RateLimit-Reset", "55"))
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].name", is("Widget Pro")));
    }

    @Test
    void getProducts_invalidApiKey_returns401() throws Exception {
        String apiKey = "ask_live_invalidkey";
        when(clientService.getClientEntityByApiKey(eq(apiKey))).thenReturn(null);

        mockMvc.perform(get("/api/demo/products")
                        .header("X-API-Key", apiKey))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status", is(401)))
                .andExpect(jsonPath("$.error", is("Unauthorized")));
    }

    @Test
    void getProducts_inactiveClient_returns403() throws Exception {
        String apiKey = "ask_live_inactiveclientkey";
        Client client = new Client(UUID.randomUUID(), "inactive-client", apiKey, ClientStatus.INACTIVE, 100, 60);
        when(clientService.getClientEntityByApiKey(eq(apiKey))).thenReturn(client);

        mockMvc.perform(get("/api/demo/products")
                        .header("X-API-Key", apiKey))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status", is(403)))
                .andExpect(jsonPath("$.error", is("Forbidden")));
    }

    @Test
    void getProducts_overLimit_returns429() throws Exception {
        String apiKey = "ask_live_validkey1234567890";
        Client client = new Client(UUID.randomUUID(), "client-app", apiKey, ClientStatus.ACTIVE, 5, 60);
        when(clientService.getClientEntityByApiKey(eq(apiKey))).thenReturn(client);
        when(rateLimitService.checkRateLimit(eq(apiKey), eq(5), eq(60)))
                .thenReturn(new RateLimitResult(false, 5, 0, 55));

        mockMvc.perform(get("/api/demo/products")
                        .header("X-API-Key", apiKey))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("X-RateLimit-Limit", "5"))
                .andExpect(header().string("X-RateLimit-Remaining", "0"))
                .andExpect(header().string("X-RateLimit-Reset", "55"))
                .andExpect(header().string("Retry-After", "55"))
                .andExpect(jsonPath("$.status", is(429)))
                .andExpect(jsonPath("$.error", is("Too Many Requests")))
                .andExpect(jsonPath("$.message", containsString("Rate limit exceeded")));
    }

    @Test
    void getProducts_rateLimitHeadersPresent() throws Exception {
        String apiKey = "ask_live_validkey1234567890";
        Client client = new Client(UUID.randomUUID(), "client-app", apiKey, ClientStatus.ACTIVE, 10, 60);
        when(clientService.getClientEntityByApiKey(eq(apiKey))).thenReturn(client);
        when(rateLimitService.checkRateLimit(eq(apiKey), eq(10), eq(60)))
                .thenReturn(new RateLimitResult(true, 10, 3, 57));

        mockMvc.perform(get("/api/demo/products")
                        .header("X-API-Key", apiKey))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-RateLimit-Limit"))
                .andExpect(header().exists("X-RateLimit-Remaining"))
                .andExpect(header().exists("X-RateLimit-Reset"));
    }
}

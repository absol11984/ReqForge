package com.apishield.controller;

import com.apishield.dto.RateLimitResult;
import com.apishield.entity.Client;
import com.apishield.entity.ClientStatus;
import com.apishield.exception.ClientInactiveException;
import com.apishield.exception.InvalidApiKeyException;
import com.apishield.exception.RateLimitExceededException;
import com.apishield.service.ClientService;
import com.apishield.service.RateLimitService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/demo")
@Tag(name = "Demo", description = "Rate-limited demo endpoints")
public class DemoController {

    private final ClientService clientService;
    private final RateLimitService rateLimitService;

    public DemoController(ClientService clientService, RateLimitService rateLimitService) {
        this.clientService = clientService;
        this.rateLimitService = rateLimitService;
    }

    @GetMapping("/products")
    @Operation(summary = "List demo products (rate limited)", description = "Returns a small product catalogue. Requires a valid X-API-Key header.")
    public ResponseEntity<List<Map<String, Object>>> getProducts(
            @Parameter(description = "Client API key", required = true)
            @RequestHeader("X-API-Key") String apiKey) {

        Client client = clientService.getClientEntityByApiKey(apiKey);
        if (client == null) {
            throw new InvalidApiKeyException("Invalid or unknown API key");
        }

        if (client.getStatus() != ClientStatus.ACTIVE) {
            throw new ClientInactiveException("Client is inactive");
        }

        RateLimitResult result = rateLimitService.checkRateLimit(
                apiKey, client.getRequestLimit(), client.getWindowSeconds());

        if (!result.allowed()) {
            throw new RateLimitExceededException("Rate limit exceeded", result);
        }

        List<Map<String, Object>> products = List.of(
                Map.of("id", 1, "name", "Widget Pro", "price", 29.99),
                Map.of("id", 2, "name", "Gadget Plus", "price", 49.99),
                Map.of("id", 3, "name", "Doohickey Max", "price", 9.99)
        );

        return ResponseEntity.ok()
                .header("X-RateLimit-Limit", String.valueOf(result.limit()))
                .header("X-RateLimit-Remaining", String.valueOf(result.remaining()))
                .header("X-RateLimit-Reset", String.valueOf(result.resetSeconds()))
                .body(products);
    }
}

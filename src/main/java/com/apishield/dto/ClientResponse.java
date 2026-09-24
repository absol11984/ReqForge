package com.apishield.dto;

import com.apishield.entity.ClientStatus;
import com.apishield.entity.RateLimitAlgorithm;
import java.time.Instant;
import java.util.UUID;

public record ClientResponse(
        UUID id,
        String name,
        String apiKey,
        ClientStatus status,
        int requestLimit,
        int windowSeconds,
        RateLimitAlgorithm algorithm,
        Instant createdAt,
        Instant updatedAt
) {
    public ClientResponse(UUID id, String name, String apiKey, ClientStatus status,
                          int requestLimit, int windowSeconds, Instant createdAt, Instant updatedAt) {
        this(id, name, apiKey, status, requestLimit, windowSeconds,
                RateLimitAlgorithm.FIXED_WINDOW, createdAt, updatedAt);
    }
}

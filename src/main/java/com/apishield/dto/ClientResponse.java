package com.apishield.dto;

import com.apishield.entity.ClientStatus;
import java.time.Instant;
import java.util.UUID;

public record ClientResponse(
        UUID id,
        String name,
        String apiKey,
        ClientStatus status,
        int requestLimit,
        int windowSeconds,
        Instant createdAt,
        Instant updatedAt
) {
    // Keep a constructor for backwards compatibility with tests that don't pass limits
    public ClientResponse(UUID id, String name, String apiKey, ClientStatus status, Instant createdAt, Instant updatedAt) {
        this(id, name, apiKey, status, 100, 60, createdAt, updatedAt);
    }
}

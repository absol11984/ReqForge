package com.apishield.dto;

import com.apishield.entity.ClientStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

public record ClientResponse(
        @Schema(description = "Client id")
        UUID id,

        @Schema(description = "Client display name")
        String name,

        @Schema(description = "Generated API key")
        String apiKey,

        @Schema(description = "Client status")
        ClientStatus status,

        @Schema(description = "Creation time")
        Instant createdAt,

        @Schema(description = "Last update time")
        Instant updatedAt
) {
}

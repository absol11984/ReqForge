package com.apishield.dto;

import com.apishield.entity.ClientStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateClientRequest(
        @Schema(description = "Client display name", example = "updated-client")
        @NotBlank(message = "name is required")
        @Size(max = 200, message = "name must be at most 200 characters")
        String name,

        @Schema(description = "Whether this client is active")
        @NotNull(message = "status is required")
        ClientStatus status
) {
}

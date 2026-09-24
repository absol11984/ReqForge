package com.apishield.dto;

import com.apishield.entity.ClientStatus;
import com.apishield.entity.RateLimitAlgorithm;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateClientRequest(
        @NotBlank(message = "name is required")
        @Size(max = 200, message = "name must be at most 200 characters")
        String name,

        @NotNull(message = "status is required")
        ClientStatus status,

        @NotNull(message = "requestLimit is required")
        @Min(value = 1, message = "requestLimit must be greater than 0")
        Integer requestLimit,

        @NotNull(message = "windowSeconds is required")
        @Min(value = 1, message = "windowSeconds must be greater than 0")
        Integer windowSeconds,

        RateLimitAlgorithm algorithm
) {
    public UpdateClientRequest(String name, ClientStatus status) {
        this(name, status, 100, 60, RateLimitAlgorithm.FIXED_WINDOW);
    }

    public UpdateClientRequest(String name, ClientStatus status, int requestLimit, int windowSeconds) {
        this(name, status, requestLimit, windowSeconds, RateLimitAlgorithm.FIXED_WINDOW);
    }
}

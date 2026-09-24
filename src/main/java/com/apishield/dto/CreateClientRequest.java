package com.apishield.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateClientRequest(
        @NotBlank(message = "name is required")
        @Size(max = 200, message = "name must be at most 200 characters")
        String name,

        @NotNull(message = "requestLimit is required")
        @Min(value = 1, message = "requestLimit must be greater than 0")
        Integer requestLimit,

        @NotNull(message = "windowSeconds is required")
        @Min(value = 1, message = "windowSeconds must be greater than 0")
        Integer windowSeconds
) {
    public CreateClientRequest(String name) {
        this(name, 100, 60);
    }
}

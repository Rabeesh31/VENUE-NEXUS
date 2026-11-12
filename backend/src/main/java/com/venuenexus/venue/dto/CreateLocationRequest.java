package com.venuenexus.venue.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreateLocationRequest(
        @NotNull(message = "Organisation ID is required")
        UUID organisationId,

        @NotBlank(message = "Location name is required")
        @Size(max = 255, message = "Location name must not exceed 255 characters")
        String name,

        String timezone,

        String currency,

        String config
) {
}

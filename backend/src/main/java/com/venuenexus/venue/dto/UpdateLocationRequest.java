package com.venuenexus.venue.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateLocationRequest(
        @NotBlank(message = "Location name is required")
        @Size(max = 255, message = "Location name must not exceed 255 characters")
        String name,

        String timezone,

        String currency,

        String config
) {
}

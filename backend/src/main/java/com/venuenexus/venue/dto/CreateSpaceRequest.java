package com.venuenexus.venue.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreateSpaceRequest(
        @NotNull(message = "Location ID is required")
        UUID locationId,

        UUID parentSpaceId,

        @NotBlank(message = "Space type is required")
        @Pattern(regexp = "^(BUILDING|FLOOR|ZONE)$", message = "Space type must be BUILDING, FLOOR, or ZONE")
        String spaceType,

        @NotBlank(message = "Space name is required")
        @Size(max = 255, message = "Space name must not exceed 255 characters")
        String name
) {
}


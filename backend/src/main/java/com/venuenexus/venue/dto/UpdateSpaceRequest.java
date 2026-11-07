package com.venuenexus.venue.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateSpaceRequest(
        @NotBlank(message = "Space name is required")
        @Size(max = 255, message = "Space name must not exceed 255 characters")
        String name
) {
}


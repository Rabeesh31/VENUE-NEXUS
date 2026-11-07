package com.venuenexus.venue.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateOrganisationRequest(
        @NotBlank(message = "Organisation name is required")
        @Size(max = 255, message = "Organisation name must not exceed 255 characters")
        String name
) {
}


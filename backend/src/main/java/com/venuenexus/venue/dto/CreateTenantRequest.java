package com.venuenexus.venue.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateTenantRequest(
        @NotBlank(message = "Tenant name is required")
        @Size(max = 255, message = "Tenant name must not exceed 255 characters")
        String name,

        String branding
) {
}

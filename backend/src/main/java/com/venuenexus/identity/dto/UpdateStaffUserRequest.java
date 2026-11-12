package com.venuenexus.identity.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record UpdateStaffUserRequest(
        @NotBlank(message = "Display name is required")
        @Size(max = 255, message = "Display name must not exceed 255 characters")
        String displayName,

        @Pattern(regexp = "^(TENANT_ADMIN|VENUE_MANAGER|SUPERVISOR|STAFF)$", message = "Invalid staff role")
        String role,

        UUID organisationId,
        UUID locationId,
        UUID departmentId,
        Boolean isAvailable
) {
}


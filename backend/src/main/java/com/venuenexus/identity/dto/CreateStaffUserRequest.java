package com.venuenexus.identity.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreateStaffUserRequest(
        @NotBlank(message = "Email is required")
        @Email(message = "Email must be valid")
        String email,

        @NotBlank(message = "Password is required")
        @Size(min = 8, message = "Password must be at least 8 characters long")
        String password,

        @NotBlank(message = "Display name is required")
        @Size(max = 255, message = "Display name must not exceed 255 characters")
        String displayName,

        @NotBlank(message = "Role is required")
        @Pattern(regexp = "^(TENANT_ADMIN|VENUE_MANAGER|SUPERVISOR|STAFF)$", message = "Invalid staff role")
        String role,

        UUID organisationId,
        UUID locationId,
        UUID departmentId
) {
}


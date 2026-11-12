package com.venuenexus.identity.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record StaffUserResponse(
        UUID id,
        UUID tenantId,
        UUID organisationId,
        UUID locationId,
        UUID departmentId,
        String email,
        String displayName,
        String role,
        String status,
        boolean isAvailable,
        OffsetDateTime lastLoginAt,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}


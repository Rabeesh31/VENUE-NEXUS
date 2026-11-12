package com.venuenexus.identity.api;

import com.venuenexus.security.StaffRole;

import java.util.UUID;

public record UserSummaryDto(
        UUID id,
        String displayName,
        StaffRole role,
        UUID tenantId,
        UUID organisationId,
        UUID locationId,
        UUID departmentId
) {
}

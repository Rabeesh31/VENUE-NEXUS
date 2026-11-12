package com.venuenexus.venue.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record DepartmentResponse(
        UUID id,
        UUID tenantId,
        UUID locationId,
        String name,
        String departmentType,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}


package com.venuenexus.venue.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record OrganisationResponse(
        UUID id,
        UUID tenantId,
        String name,
        String status,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}


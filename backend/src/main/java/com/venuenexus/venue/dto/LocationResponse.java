package com.venuenexus.venue.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record LocationResponse(
        UUID id,
        UUID tenantId,
        UUID organisationId,
        String name,
        String timezone,
        String currency,
        String config,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}

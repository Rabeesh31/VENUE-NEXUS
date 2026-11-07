package com.venuenexus.venue.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record SpaceResponse(
        UUID id,
        UUID tenantId,
        UUID locationId,
        UUID parentSpaceId,
        String spaceType,
        String name,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}


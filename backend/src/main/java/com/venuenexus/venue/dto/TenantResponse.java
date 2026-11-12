package com.venuenexus.venue.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record TenantResponse(
        UUID id,
        String name,
        String status,
        String branding,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}

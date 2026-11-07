package com.venuenexus.security.authz;

import java.util.UUID;

public record TestRecord(
        UUID id,
        UUID tenantId,
        UUID organisationId,
        UUID locationId,
        String name
) {}


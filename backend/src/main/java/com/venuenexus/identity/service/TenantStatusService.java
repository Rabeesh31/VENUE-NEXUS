package com.venuenexus.identity.service;

import java.util.UUID;

/**
 * Service to verify tenant operational status during login and token refresh.
 */
public interface TenantStatusService {
    boolean isTenantSuspended(UUID tenantId);
}

package com.venuenexus.security.authz;

import com.venuenexus.common.error.ApiException;
import com.venuenexus.common.error.ErrorCode;
import com.venuenexus.security.context.TenantContext;

import java.util.UUID;

/**
 * Security guard enforcing strict tenant isolation on database records.
 *
 * <p><strong>CRITICAL SECURITY INVARIANT:</strong>
 * Cross-tenant access attempts MUST yield {@link ErrorCode#RESOURCE_NOT_FOUND} (404) rather than
 * 403 Forbidden to ensure tenant data existence is never revealed to unauthorized callers.
 */
public final class TenantScopedGuard {

    private TenantScopedGuard() {
        // Prevent instantiation
    }

    /**
     * Asserts that the entity's tenant ID strictly matches the authenticated caller's tenant ID.
     *
     * @param recordTenantId the tenant ID from the loaded record
     * @throws ApiException with {@link ErrorCode#RESOURCE_NOT_FOUND} (404) if the record belongs to another tenant
     */
    public static void assertSameTenant(UUID recordTenantId) {
        UUID currentTenantId = TenantContext.currentTenantId().orElse(null);
        if (currentTenantId == null || recordTenantId == null || !recordTenantId.equals(currentTenantId)) {
            throw new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Resource not found");
        }
    }
}

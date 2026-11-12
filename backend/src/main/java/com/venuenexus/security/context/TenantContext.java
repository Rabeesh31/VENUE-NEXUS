package com.venuenexus.security.context;

import com.venuenexus.common.error.ApiException;
import com.venuenexus.common.error.ErrorCode;

import java.util.Optional;
import java.util.UUID;

/**
 * ThreadLocal context holding the authenticated staff member's tenant ID.
 *
 * <p><strong>CRITICAL SECURITY INVARIANT:</strong>
 * Tenant identity MUST strictly originate from a validated, server-side verified staff JWT.
 * It must NEVER be accepted or overridden from HTTP request bodies, path parameters,
 * query parameters, or client headers.
 *
 * <p>Thread Safety: Stored per-thread in a {@link ThreadLocal}. Must be cleared after
 * every request in the filter's {@code finally} block to prevent leakage across pooled threads.
 */
public final class TenantContext {

    private static final ThreadLocal<UUID> CURRENT_TENANT = new ThreadLocal<>();

    private TenantContext() {
        // Prevent instantiation
    }

    /**
     * Retrieves the optional tenant ID associated with the current thread.
     * Will return empty for platform administrators who are not scoped to a specific tenant,
     * or for unauthenticated requests.
     *
     * @return an {@link Optional} containing the tenant ID if present, or empty.
     */
    public static Optional<UUID> currentTenantId() {
        return Optional.ofNullable(CURRENT_TENANT.get());
    }

    /**
     * Retrieves the mandatory tenant ID for the current request.
     *
     * @return the tenant {@link UUID}
     * @throws ApiException with {@link ErrorCode#ACCESS_DENIED} if no tenant ID is present on the thread.
     */
    public static UUID requireTenantId() {
        return currentTenantId().orElseThrow(() ->
                new ApiException(ErrorCode.ACCESS_DENIED, "Tenant context required for this operation")
        );
    }

    /**
     * Sets the tenant ID for the current thread.
     * Restricted to security infrastructure (e.g. JWT authentication filters).
     *
     * @param tenantId the validated tenant ID, or null to clear.
     */
    public static void setTenantId(UUID tenantId) {
        if (tenantId == null) {
            CURRENT_TENANT.remove();
        } else {
            CURRENT_TENANT.set(tenantId);
        }
    }

    /**
     * Clears the tenant context from the current thread.
     */
    public static void clear() {
        CURRENT_TENANT.remove();
    }
}


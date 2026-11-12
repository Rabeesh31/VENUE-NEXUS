package com.venuenexus.security.context;

import com.venuenexus.common.error.ApiException;
import com.venuenexus.common.error.ErrorCode;
import com.venuenexus.security.StaffRole;

import java.util.Optional;
import java.util.UUID;

/**
 * Immutable scope context capturing the operational boundaries of the authenticated staff member.
 *
 * <p><strong>CRITICAL SECURITY INVARIANT:</strong>
 * Scope constraints (organisation, location, department) are derived exclusively from the validated staff JWT.
 * Cross-location or cross-organisation access by scoped staff is strictly forbidden and must be denied server-side.
 *
 * @param role the staff member's role
 * @param tenantId the tenant ID (null for PLATFORM_ADMIN)
 * @param organisationId the scoped organisation ID (optional)
 * @param locationId the scoped location ID (optional)
 * @param departmentId the scoped department ID (optional)
 */
public record ScopeContext(
        StaffRole role,
        UUID tenantId,
        UUID organisationId,
        UUID locationId,
        UUID departmentId
) {
    private static final ThreadLocal<ScopeContext> CURRENT_SCOPE = new ThreadLocal<>();

    /**
     * Retrieves the optional {@link ScopeContext} associated with the current thread.
     *
     * @return an {@link Optional} containing the ScopeContext, or empty if unauthenticated.
     */
    public static Optional<ScopeContext> current() {
        return Optional.ofNullable(CURRENT_SCOPE.get());
    }

    /**
     * Retrieves the mandatory {@link ScopeContext} for the current request.
     *
     * @return the {@link ScopeContext}
     * @throws ApiException with {@link ErrorCode#ACCESS_DENIED} if unauthenticated.
     */
    public static ScopeContext require() {
        return current().orElseThrow(() ->
                new ApiException(ErrorCode.ACCESS_DENIED, "Scope context required for this operation")
        );
    }

    /**
     * Sets the scope context on the current thread.
     *
     * @param context the verified scope context, or null to clear.
     */
    public static void set(ScopeContext context) {
        if (context == null) {
            CURRENT_SCOPE.remove();
        } else {
            CURRENT_SCOPE.set(context);
        }
    }

    /**
     * Clears the scope context from the current thread.
     */
    public static void clear() {
        CURRENT_SCOPE.remove();
    }

    /**
     * Checks if the caller is platform-scoped (system-wide platform operations only).
     */
    public boolean isPlatformScoped() {
        return role == StaffRole.PLATFORM_ADMIN;
    }

    /**
     * Checks if the caller is tenant-level (access to all organisations and locations within their tenant).
     */
    public boolean isTenantLevel() {
        return !isPlatformScoped() && organisationId == null && locationId == null;
    }

    /**
     * Checks if the caller is restricted to a specific organisation (can access all locations within that org).
     */
    public boolean isOrganisationScoped() {
        return organisationId != null && locationId == null;
    }

    /**
     * Checks if the caller is restricted to a specific physical location.
     */
    public boolean isLocationScoped() {
        return locationId != null;
    }
}


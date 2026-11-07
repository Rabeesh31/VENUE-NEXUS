package com.venuenexus.security.context;

import com.venuenexus.common.error.ApiException;
import com.venuenexus.common.error.ErrorCode;
import com.venuenexus.security.StaffRole;

import java.util.Optional;
import java.util.UUID;

/**
 * Immutable identity snapshot of the currently authenticated staff user.
 *
 * @param userId the unique user ID (app_user.id)
 * @param role the user's role
 * @param tenantId the user's tenant ID (null for PLATFORM_ADMIN)
 * @param organisationId the scoped organisation ID (optional)
 * @param locationId the scoped location ID (optional)
 * @param departmentId the scoped department ID (optional)
 * @param tokenId the unique access token ID (jti)
 */
public record CurrentStaff(
        UUID userId,
        StaffRole role,
        UUID tenantId,
        UUID organisationId,
        UUID locationId,
        UUID departmentId,
        String tokenId
) {
    private static final ThreadLocal<CurrentStaff> CURRENT_STAFF = new ThreadLocal<>();

    /**
     * Retrieves the optional {@link CurrentStaff} associated with the current thread.
     *
     * @return an {@link Optional} containing CurrentStaff, or empty if unauthenticated.
     */
    public static Optional<CurrentStaff> current() {
        return Optional.ofNullable(CURRENT_STAFF.get());
    }

    /**
     * Retrieves the mandatory {@link CurrentStaff} for the current request.
     *
     * @return the {@link CurrentStaff}
     * @throws ApiException with {@link ErrorCode#ACCESS_DENIED} if unauthenticated.
     */
    public static CurrentStaff require() {
        return current().orElseThrow(() ->
                new ApiException(ErrorCode.ACCESS_DENIED, "Authentication required for current staff identity")
        );
    }

    /**
     * Sets the CurrentStaff identity for the current thread.
     *
     * @param staff the staff identity
     */
    public static void set(CurrentStaff staff) {
        CURRENT_STAFF.set(staff);
    }

    /**
     * Clears the CurrentStaff context from the current thread.
     */
    public static void clear() {
        CURRENT_STAFF.remove();
    }
}

package com.venuenexus.security;

/**
 * Platform staff role enumeration matching docs/security.md §2 and Flyway V2 app_user check constraint.
 */
public enum StaffRole {
    PLATFORM_ADMIN,
    TENANT_ADMIN,
    VENUE_MANAGER,
    SUPERVISOR,
    STAFF;

    public static StaffRole fromString(String role) {
        if (role == null) {
            return null;
        }
        return StaffRole.valueOf(role.trim().toUpperCase());
    }
}


package com.venuenexus.security.authz;

/**
 * Reusable expressions for method-level security with {@code @PreAuthorize}.
 * Centralises all role-based authorization rules so that changes to the permission
 * matrix can be made in a single location.
 */
public final class SecurityExpressions {

    private SecurityExpressions() {
        // Prevent instantiation
    }

    public static final String PLATFORM_ONLY = "hasRole('" + Roles.PLATFORM_ADMIN + "')";
    public static final String TENANT_ADMIN_ONLY = "hasRole('" + Roles.TENANT_ADMIN + "')";
    public static final String MANAGER_AND_ABOVE = "hasAnyRole('" + Roles.TENANT_ADMIN + "','" + Roles.VENUE_MANAGER + "')";
    public static final String SUPERVISOR_AND_ABOVE = "hasAnyRole('" + Roles.TENANT_ADMIN + "','" + Roles.VENUE_MANAGER + "','" + Roles.SUPERVISOR + "')";
    public static final String ANY_STAFF_ROLE = "hasAnyRole('" + Roles.TENANT_ADMIN + "','" + Roles.VENUE_MANAGER + "','" + Roles.SUPERVISOR + "','" + Roles.STAFF + "')";
}


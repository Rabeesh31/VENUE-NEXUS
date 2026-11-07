package com.venuenexus.security.authz;

/**
 * Standard role string constants for Spring Security authorities.
 * All authorities are prefixed with {@code ROLE_} internally, while Spring Security's
 * {@code hasRole(...)} automatically strips or expects this prefix.
 */
public final class Roles {

    private Roles() {
        // Prevent instantiation
    }

    public static final String PLATFORM_ADMIN = "PLATFORM_ADMIN";
    public static final String TENANT_ADMIN = "TENANT_ADMIN";
    public static final String VENUE_MANAGER = "VENUE_MANAGER";
    public static final String SUPERVISOR = "SUPERVISOR";
    public static final String STAFF = "STAFF";

    // Prefixed authorities for UserDetails and GrantedAuthority mappings
    public static final String PREFIX = "ROLE_";
    public static final String ROLE_PLATFORM_ADMIN = PREFIX + PLATFORM_ADMIN;
    public static final String ROLE_TENANT_ADMIN = PREFIX + TENANT_ADMIN;
    public static final String ROLE_VENUE_MANAGER = PREFIX + VENUE_MANAGER;
    public static final String ROLE_SUPERVISOR = PREFIX + SUPERVISOR;
    public static final String ROLE_STAFF = PREFIX + STAFF;
}


package com.venuenexus.identity.model;

/**
 * Staff user status enumeration matching Flyway V2 app_user check constraint:
 * ('ACTIVE', 'SUSPENDED', 'DEACTIVATED').
 */
public enum UserStatus {
    ACTIVE,
    SUSPENDED,
    DEACTIVATED;

    public static UserStatus fromString(String status) {
        if (status == null) {
            return DEACTIVATED;
        }
        String normalized = status.trim().toUpperCase();
        if ("INACTIVE".equals(normalized)) {
            return DEACTIVATED;
        }
        try {
            return UserStatus.valueOf(normalized);
        } catch (IllegalArgumentException e) {
            return DEACTIVATED;
        }
    }
}


package com.venuenexus.security.jwt;

/**
 * Token type discriminator for VENUE NEXUS tokens.
 * Staff access tokens MUST carry typ=STAFF.
 */
public enum TokenType {
    STAFF;

    public static final String STAFF_VALUE = "STAFF";
}


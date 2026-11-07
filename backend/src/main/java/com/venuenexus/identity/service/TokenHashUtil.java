package com.venuenexus.identity.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Utility for SHA-256 cryptographic hashing of refresh tokens.
 *
 * <p><strong>CRITICAL SECURITY INVARIANT:</strong>
 * Refresh tokens are NEVER stored or logged in plaintext. Only their SHA-256 hex digest
 * is persisted in the database.
 */
public final class TokenHashUtil {

    private TokenHashUtil() {
        // Prevent instantiation
    }

    public static String sha256Hex(String rawToken) {
        if (rawToken == null) {
            throw new IllegalArgumentException("Token cannot be null");
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashBytes);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 message digest algorithm not available", ex);
        }
    }
}


package com.venuenexus.resource.qr;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Generates and hashes QR tokens. Pure Java, no framework dependency.
 *
 * Token: 128 random bits, base64url without padding (22 characters).
 * Only SHA-256(token) is ever stored. The plain token must never be logged.
 */
public final class QrTokenService {

    public static final int TOKEN_BYTES = 16; // 128 bits
    public static final int TOKEN_LENGTH = 22; // base64url, no padding

    private final SecureRandom random;

    public QrTokenService() {
        this(new SecureRandom());
    }

    QrTokenService(SecureRandom random) {
        this.random = random;
    }

    public String generateToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** Lower-case hex SHA-256 (64 chars). Use this one format everywhere (DB column, lookups). */
    public String hash(String token) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    /** True only for a well-formed token; lets callers reject junk before any DB lookup. */
    public boolean isWellFormed(String token) {
        if (token == null || token.length() != TOKEN_LENGTH) {
            return false;
        }
        for (int i = 0; i < token.length(); i++) {
            char c = token.charAt(i);
            boolean ok = (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z')
                    || (c >= '0' && c <= '9') || c == '-' || c == '_';
            if (!ok) {
                return false;
            }
        }
        return true;
    }
}

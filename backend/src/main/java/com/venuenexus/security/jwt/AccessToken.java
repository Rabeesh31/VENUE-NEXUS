package com.venuenexus.security.jwt;

import java.time.Instant;

/**
 * Immutable wrapper around an issued JWT access token and its metadata.
 *
 * @param token the encoded JWT string
 * @param jti the unique JWT ID (used for token revocation and denylisting)
 * @param expiresAt the timestamp at which the token expires
 */
public record AccessToken(
        String token,
        String jti,
        Instant expiresAt
) {
}


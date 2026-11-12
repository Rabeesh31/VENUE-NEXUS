package com.venuenexus.security.jwt;

import com.venuenexus.security.StaffRole;

import java.time.Instant;
import java.util.UUID;

/**
 * Validated claims extracted from a staff access JWT.
 *
 * @param uid the user ID (app_user.id)
 * @param typ the token type (must be STAFF)
 * @param rol the staff role
 * @param tid the tenant ID (null for PLATFORM_ADMIN)
 * @param oid the organisation scope ID (optional)
 * @param lid the location scope ID (optional)
 * @param did the department scope ID (optional)
 * @param jti the unique token ID
 * @param aud the audience
 * @param iss the issuer
 * @param iat the issue timestamp
 * @param exp the expiration timestamp
 */
public record StaffTokenClaims(
        UUID uid,
        String typ,
        StaffRole rol,
        UUID tid,
        UUID oid,
        UUID lid,
        UUID did,
        String jti,
        String aud,
        String iss,
        Instant iat,
        Instant exp
) {
}

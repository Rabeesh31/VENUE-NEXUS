package com.venuenexus.security.jwt;

import com.venuenexus.common.error.ApiException;
import com.venuenexus.common.error.ErrorCode;
import com.venuenexus.security.StaffRole;
import com.venuenexus.security.context.CurrentStaff;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Set;
import java.util.UUID;

/**
 * Service responsible for issuing, parsing, and validating staff access JWTs.
 *
 * <p><strong>CRITICAL SECURITY INVARIANTS:</strong>
 * <ul>
 *   <li>Algorithm is pinned strictly to HS256. All other algorithms (including 'none') are rejected.</li>
 *   <li>Secrets MUST be at least 32 bytes (256 bits). Application fails startup immediately if insufficient.</li>
 *   <li>Token type MUST be 'STAFF'.</li>
 *   <li>Tokens never carry prices, business data, or personal details (e.g. email or display name).</li>
 *   <li>Absent claims (e.g. tid for PLATFORM_ADMIN) are omitted entirely, never serialized as null.</li>
 * </ul>
 */
@Service
public class StaffJwtService {

    public static final String CLAIM_UID = "uid";
    public static final String CLAIM_TYP = "typ";
    public static final String CLAIM_ROL = "rol";
    public static final String CLAIM_TID = "tid";
    public static final String CLAIM_OID = "oid";
    public static final String CLAIM_LID = "lid";
    public static final String CLAIM_DID = "did";
    public static final String TOKEN_TYPE_STAFF = "STAFF";

    private final SecretKey secretKey;
    private final String issuer;
    private final String audience;
    private final Duration accessTtl;
    private final Duration clockSkew;
    private final Clock clock;

    public StaffJwtService(
            @Value("${security.jwt.staff-secret}") String secretString,
            @Value("${security.jwt.issuer:venue-nexus}") String issuer,
            @Value("${security.jwt.audience:venue-nexus-staff}") String audience,
            @Value("${security.jwt.access-ttl:PT15M}") Duration accessTtl,
            @Value("${security.jwt.leeway:PT0S}") Duration clockSkew,
            Clock clock) {

        if (secretString == null || secretString.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("JWT_STAFF_SECRET is missing or must be at least 32 bytes (256 bits)");
        }

        this.secretKey = Keys.hmacShaKeyFor(secretString.getBytes(StandardCharsets.UTF_8));
        this.issuer = issuer;
        this.audience = audience;
        this.accessTtl = accessTtl;
        this.clockSkew = clockSkew != null ? clockSkew : Duration.ZERO;
        this.clock = clock;
    }

    /**
     * Issues an access JWT for the given CurrentStaff identity.
     */
    public AccessToken issueToken(CurrentStaff staff) {
        return issueToken(
                staff.userId(),
                staff.role(),
                staff.tenantId(),
                staff.organisationId(),
                staff.locationId(),
                staff.departmentId()
        );
    }

    /**
     * Issues an access JWT with standard claims.
     */
    public AccessToken issueToken(
            UUID userId,
            StaffRole role,
            UUID tenantId,
            UUID organisationId,
            UUID locationId,
            UUID departmentId) {

        if (userId == null || role == null) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "userId and role are required to issue access token");
        }

        Instant now = clock.instant();
        Instant expiresAt = now.plus(accessTtl);
        String jti = UUID.randomUUID().toString();

        var builder = Jwts.builder()
                .id(jti)
                .issuer(issuer)
                .audience().add(audience).and()
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .claim(CLAIM_UID, userId.toString())
                .claim(CLAIM_TYP, TOKEN_TYPE_STAFF)
                .claim(CLAIM_ROL, role.name());

        // Omit absent claims; never emit null
        if (tenantId != null) {
            builder.claim(CLAIM_TID, tenantId.toString());
        }
        if (organisationId != null) {
            builder.claim(CLAIM_OID, organisationId.toString());
        }
        if (locationId != null) {
            builder.claim(CLAIM_LID, locationId.toString());
        }
        if (departmentId != null) {
            builder.claim(CLAIM_DID, departmentId.toString());
        }

        String jwtString = builder.signWith(secretKey, Jwts.SIG.HS256).compact();
        return new AccessToken(jwtString, jti, expiresAt);
    }

    /**
     * Parses and cryptographically validates the given staff access token.
     *
     * @param jwtString the raw JWT string
     * @return validated {@link StaffTokenClaims}
     * @throws ApiException with precise {@link ErrorCode} mapping on validation failure
     */
    public StaffTokenClaims parseAndValidate(String jwtString) {
        if (jwtString == null || jwtString.isBlank()) {
            throw new ApiException(ErrorCode.AUTH_TOKEN_INVALID, "Token is missing or blank");
        }

        Claims claims;
        try {
            claims = Jwts.parser()
                    .verifyWith(secretKey)
                    .clockSkewSeconds(clockSkew.toSeconds())
                    .clock(() -> Date.from(clock.instant()))
                    .build()
                    .parseSignedClaims(jwtString)
                    .getPayload();
        } catch (ExpiredJwtException ex) {
            throw new ApiException(ErrorCode.AUTH_TOKEN_EXPIRED, ErrorCode.AUTH_TOKEN_EXPIRED.getDefaultMessage(), ex);
        } catch (JwtException | IllegalArgumentException ex) {
            throw new ApiException(ErrorCode.AUTH_TOKEN_INVALID, ErrorCode.AUTH_TOKEN_INVALID.getDefaultMessage(), ex);
        }

        // Validate typ == STAFF
        String typ = claims.get(CLAIM_TYP, String.class);
        if (!TOKEN_TYPE_STAFF.equals(typ)) {
            throw new ApiException(ErrorCode.AUTH_TOKEN_WRONG_TYPE, ErrorCode.AUTH_TOKEN_WRONG_TYPE.getDefaultMessage());
        }

        // Validate audience and issuer
        Set<String> audSet = claims.getAudience();
        if (audSet == null || !audSet.contains(audience)) {
            throw new ApiException(ErrorCode.AUTH_TOKEN_INVALID_AUDIENCE, ErrorCode.AUTH_TOKEN_INVALID_AUDIENCE.getDefaultMessage());
        }
        if (issuer != null && !issuer.equals(claims.getIssuer())) {
            throw new ApiException(ErrorCode.AUTH_TOKEN_INVALID_AUDIENCE, ErrorCode.AUTH_TOKEN_INVALID_AUDIENCE.getDefaultMessage());
        }

        // Parse mandatory claims
        String uidStr = claims.get(CLAIM_UID, String.class);
        String rolStr = claims.get(CLAIM_ROL, String.class);
        String jti = claims.getId();

        if (uidStr == null || rolStr == null || jti == null) {
            throw new ApiException(ErrorCode.AUTH_TOKEN_INVALID, "Required claims missing");
        }

        UUID uid;
        StaffRole rol;
        try {
            uid = UUID.fromString(uidStr);
            rol = StaffRole.valueOf(rolStr);
        } catch (IllegalArgumentException ex) {
            throw new ApiException(ErrorCode.AUTH_TOKEN_INVALID, "Malformed uid or rol claim", ex);
        }

        // Optional claims
        UUID tid = parseOptionalUuid(claims.get(CLAIM_TID, String.class));
        UUID oid = parseOptionalUuid(claims.get(CLAIM_OID, String.class));
        UUID lid = parseOptionalUuid(claims.get(CLAIM_LID, String.class));
        UUID did = parseOptionalUuid(claims.get(CLAIM_DID, String.class));

        Instant iat = claims.getIssuedAt() != null ? claims.getIssuedAt().toInstant() : null;
        Instant exp = claims.getExpiration() != null ? claims.getExpiration().toInstant() : null;

        return new StaffTokenClaims(
                uid,
                typ,
                rol,
                tid,
                oid,
                lid,
                did,
                jti,
                audience,
                issuer,
                iat,
                exp
        );
    }

    private UUID parseOptionalUuid(String str) {
        if (str == null || str.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(str);
        } catch (IllegalArgumentException ex) {
            throw new ApiException(ErrorCode.AUTH_TOKEN_INVALID, "Malformed UUID in claim", ex);
        }
    }

    public SecretKey getSecretKey() {
        return secretKey;
    }

    public Duration getAccessTtl() {
        return accessTtl;
    }
}


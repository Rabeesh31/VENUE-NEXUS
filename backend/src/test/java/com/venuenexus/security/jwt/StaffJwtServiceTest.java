package com.venuenexus.security.jwt;

import com.venuenexus.common.error.ApiException;
import com.venuenexus.common.error.ErrorCode;
import com.venuenexus.security.StaffRole;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StaffJwtServiceTest {

    private static final String VALID_SECRET = "very-secure-secret-key-that-is-at-least-32-bytes-long!";
    private static final String WRONG_SECRET = "another-different-key-that-is-also-at-least-32-bytes!";
    private static final Instant BASE_TIME = Instant.parse("2026-11-07T12:00:00Z");

    private MutableClock testClock;
    private StaffJwtService jwtService;

    @BeforeEach
    void setUp() {
        testClock = new MutableClock(BASE_TIME);
        jwtService = new StaffJwtService(
                VALID_SECRET,
                "venue-nexus",
                "venue-nexus-staff",
                Duration.ofMinutes(15),
                Duration.ZERO,
                testClock
        );
    }

    @Test
    void testFailFast_SecretMissingOrLessThan32Bytes() {
        assertThatThrownBy(() -> new StaffJwtService(
                null, "venue-nexus", "venue-nexus-staff", Duration.ofMinutes(15), Duration.ZERO, testClock
        )).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("at least 32 bytes");

        assertThatThrownBy(() -> new StaffJwtService(
                "short-secret-under-32-bytes", "venue-nexus", "venue-nexus-staff", Duration.ofMinutes(15), Duration.ZERO, testClock
        )).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("at least 32 bytes");
    }

    @Test
    void testRoundTrip_ValidToken() {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        UUID locId = UUID.randomUUID();
        UUID depId = UUID.randomUUID();

        AccessToken accessToken = jwtService.issueToken(userId, StaffRole.VENUE_MANAGER, tenantId, orgId, locId, depId);

        assertThat(accessToken.token()).isNotBlank();
        assertThat(accessToken.jti()).isNotBlank();
        assertThat(accessToken.expiresAt()).isEqualTo(BASE_TIME.plus(Duration.ofMinutes(15)));

        StaffTokenClaims claims = jwtService.parseAndValidate(accessToken.token());

        assertThat(claims.uid()).isEqualTo(userId);
        assertThat(claims.typ()).isEqualTo("STAFF");
        assertThat(claims.rol()).isEqualTo(StaffRole.VENUE_MANAGER);
        assertThat(claims.tid()).isEqualTo(tenantId);
        assertThat(claims.oid()).isEqualTo(orgId);
        assertThat(claims.lid()).isEqualTo(locId);
        assertThat(claims.did()).isEqualTo(depId);
        assertThat(claims.jti()).isEqualTo(accessToken.jti());
        assertThat(claims.aud()).isEqualTo("venue-nexus-staff");
        assertThat(claims.iss()).isEqualTo("venue-nexus");
        assertThat(claims.iat()).isEqualTo(BASE_TIME);
        assertThat(claims.exp()).isEqualTo(BASE_TIME.plus(Duration.ofMinutes(15)));
    }

    @Test
    void testOptionalClaimsOmitted_PlatformAdmin() {
        UUID userId = UUID.randomUUID();
        AccessToken accessToken = jwtService.issueToken(userId, StaffRole.PLATFORM_ADMIN, null, null, null, null);

        StaffTokenClaims claims = jwtService.parseAndValidate(accessToken.token());

        assertThat(claims.uid()).isEqualTo(userId);
        assertThat(claims.rol()).isEqualTo(StaffRole.PLATFORM_ADMIN);
        assertThat(claims.tid()).isNull();
        assertThat(claims.oid()).isNull();
        assertThat(claims.lid()).isNull();
        assertThat(claims.did()).isNull();
    }

    @Test
    void testExpiredToken_ThrowsAuthTokenExpired_T20() {
        AccessToken accessToken = jwtService.issueToken(UUID.randomUUID(), StaffRole.STAFF, UUID.randomUUID(), null, null, null);

        // Advance clock past expiration (15 min + 1 second)
        testClock.setInstant(BASE_TIME.plus(Duration.ofMinutes(15)).plusSeconds(1));

        assertThatThrownBy(() -> jwtService.parseAndValidate(accessToken.token()))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getErrorCode()).isEqualTo(ErrorCode.AUTH_TOKEN_EXPIRED));
    }

    @Test
    void testTamperedSignature_ThrowsAuthTokenInvalid_T21() {
        AccessToken accessToken = jwtService.issueToken(UUID.randomUUID(), StaffRole.STAFF, UUID.randomUUID(), null, null, null);

        // Tamper token string by altering the last character
        String tampered = accessToken.token().substring(0, accessToken.token().length() - 4) + "XyZ1";

        assertThatThrownBy(() -> jwtService.parseAndValidate(tampered))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getErrorCode()).isEqualTo(ErrorCode.AUTH_TOKEN_INVALID));
    }

    @Test
    void testWrongType_ThrowsAuthTokenWrongType_T22() {
        SecretKey key = Keys.hmacShaKeyFor(VALID_SECRET.getBytes(StandardCharsets.UTF_8));
        String tokenWithWrongTyp = Jwts.builder()
                .id(UUID.randomUUID().toString())
                .issuer("venue-nexus")
                .audience().add("venue-nexus-staff").and()
                .issuedAt(Date.from(BASE_TIME))
                .expiration(Date.from(BASE_TIME.plus(Duration.ofMinutes(15))))
                .claim(StaffJwtService.CLAIM_UID, UUID.randomUUID().toString())
                .claim(StaffJwtService.CLAIM_TYP, "CUSTOMER") // Wrong type!
                .claim(StaffJwtService.CLAIM_ROL, "STAFF")
                .signWith(key, Jwts.SIG.HS256)
                .compact();

        assertThatThrownBy(() -> jwtService.parseAndValidate(tokenWithWrongTyp))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getErrorCode()).isEqualTo(ErrorCode.AUTH_TOKEN_WRONG_TYPE));
    }

    @Test
    void testWrongAudienceOrIssuer_ThrowsAuthTokenInvalidAudience_T23() {
        SecretKey key = Keys.hmacShaKeyFor(VALID_SECRET.getBytes(StandardCharsets.UTF_8));

        // Wrong audience
        String wrongAudience = Jwts.builder()
                .id(UUID.randomUUID().toString())
                .issuer("venue-nexus")
                .audience().add("wrong-audience").and()
                .issuedAt(Date.from(BASE_TIME))
                .expiration(Date.from(BASE_TIME.plus(Duration.ofMinutes(15))))
                .claim(StaffJwtService.CLAIM_UID, UUID.randomUUID().toString())
                .claim(StaffJwtService.CLAIM_TYP, "STAFF")
                .claim(StaffJwtService.CLAIM_ROL, "STAFF")
                .signWith(key, Jwts.SIG.HS256)
                .compact();

        assertThatThrownBy(() -> jwtService.parseAndValidate(wrongAudience))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getErrorCode()).isEqualTo(ErrorCode.AUTH_TOKEN_INVALID_AUDIENCE));

        // Wrong issuer
        String wrongIssuer = Jwts.builder()
                .id(UUID.randomUUID().toString())
                .issuer("wrong-issuer")
                .audience().add("venue-nexus-staff").and()
                .issuedAt(Date.from(BASE_TIME))
                .expiration(Date.from(BASE_TIME.plus(Duration.ofMinutes(15))))
                .claim(StaffJwtService.CLAIM_UID, UUID.randomUUID().toString())
                .claim(StaffJwtService.CLAIM_TYP, "STAFF")
                .claim(StaffJwtService.CLAIM_ROL, "STAFF")
                .signWith(key, Jwts.SIG.HS256)
                .compact();

        assertThatThrownBy(() -> jwtService.parseAndValidate(wrongIssuer))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getErrorCode()).isEqualTo(ErrorCode.AUTH_TOKEN_INVALID_AUDIENCE));
    }

    @Test
    void testAlgorithmNone_ThrowsAuthTokenInvalid_T24() {
        // Construct an unsigned / alg=none token: header.payload.
        String header = "eyJhbGciOiJub25lIiwidHlwIjoiSldUIn0"; // {"alg":"none","typ":"JWT"}
        String payload = "eyJ1aWQiOiIxMjMiLCJ0eXAiOiJTVEFGRiIsInJvbCI6IlNUQUZGIiwic3ViIjoidGVzdCJ9";
        String unsignedToken = header + "." + payload + ".";

        assertThatThrownBy(() -> jwtService.parseAndValidate(unsignedToken))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getErrorCode()).isEqualTo(ErrorCode.AUTH_TOKEN_INVALID));
    }

    @Test
    void testSignedWithDifferentKey_ThrowsAuthTokenInvalid_T25() {
        SecretKey differentKey = Keys.hmacShaKeyFor(WRONG_SECRET.getBytes(StandardCharsets.UTF_8));
        String tokenWithDifferentKey = Jwts.builder()
                .id(UUID.randomUUID().toString())
                .issuer("venue-nexus")
                .audience().add("venue-nexus-staff").and()
                .issuedAt(Date.from(BASE_TIME))
                .expiration(Date.from(BASE_TIME.plus(Duration.ofMinutes(15))))
                .claim(StaffJwtService.CLAIM_UID, UUID.randomUUID().toString())
                .claim(StaffJwtService.CLAIM_TYP, "STAFF")
                .claim(StaffJwtService.CLAIM_ROL, "STAFF")
                .signWith(differentKey, Jwts.SIG.HS256)
                .compact();

        assertThatThrownBy(() -> jwtService.parseAndValidate(tokenWithDifferentKey))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getErrorCode()).isEqualTo(ErrorCode.AUTH_TOKEN_INVALID));
    }

    private static class MutableClock extends Clock {
        private Instant currentInstant;

        public MutableClock(Instant initial) {
            this.currentInstant = initial;
        }

        public void setInstant(Instant instant) {
            this.currentInstant = instant;
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return currentInstant;
        }
    }
}


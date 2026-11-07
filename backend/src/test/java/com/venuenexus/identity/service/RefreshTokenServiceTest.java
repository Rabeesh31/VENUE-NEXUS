package com.venuenexus.identity.service;

import com.venuenexus.audit.model.AuditEvent;
import com.venuenexus.audit.model.AuditEventType;
import com.venuenexus.audit.service.AuditLogService;
import com.venuenexus.common.error.ApiException;
import com.venuenexus.common.error.ErrorCode;
import com.venuenexus.identity.api.LoginResponse;
import com.venuenexus.identity.entity.AppUser;
import com.venuenexus.identity.entity.RefreshToken;
import com.venuenexus.identity.model.UserStatus;
import com.venuenexus.identity.repository.AppUserRepository;
import com.venuenexus.identity.repository.RefreshTokenRepository;
import com.venuenexus.security.StaffRole;
import com.venuenexus.security.jwt.AccessToken;
import com.venuenexus.security.jwt.StaffJwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private AppUserRepository appUserRepository;

    @Mock
    private StaffJwtService staffJwtService;

    @Mock
    private AuditLogService auditLogService;

    @Mock
    private LoginAttemptService loginAttemptService;

    private DefaultTenantStatusService tenantStatusService;
    private MutableClock testClock;
    private RefreshTokenService refreshTokenService;

    private static final Instant BASE_TIME = Instant.parse("2026-11-07T12:00:00Z");

    @BeforeEach
    void setUp() {
        testClock = new MutableClock(BASE_TIME);
        tenantStatusService = new DefaultTenantStatusService();
        lenient().when(staffJwtService.getAccessTtl()).thenReturn(Duration.ofMinutes(15));
        lenient().when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(inv -> inv.getArgument(0));

        refreshTokenService = new RefreshTokenService(
                refreshTokenRepository,
                appUserRepository,
                staffJwtService,
                auditLogService,
                tenantStatusService,
                loginAttemptService,
                testClock,
                Duration.ofDays(7)
        );
    }

    @Test
    void testRotate_Success_T30() {
        String rawToken = "valid-raw-refresh-token-string";
        String tokenHash = TokenHashUtil.sha256Hex(rawToken);

        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID familyId = UUID.randomUUID();

        RefreshToken existingToken = RefreshToken.builder()
                .id(UUID.randomUUID())
                .tokenHash(tokenHash)
                .tokenFamily(familyId)
                .userId(userId)
                .expiresAt(BASE_TIME.plus(Duration.ofDays(7)).atOffset(ZoneOffset.UTC))
                .build();

        AppUser user = AppUser.builder()
                .tenantId(tenantId)
                .role(StaffRole.VENUE_MANAGER.name())
                .status(UserStatus.ACTIVE.name())
                .available(true)
                .displayName("Manager")
                .build();
        user.setId(userId);

        when(refreshTokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(existingToken));
        when(appUserRepository.findById(userId)).thenReturn(Optional.of(user));
        when(staffJwtService.issueToken(eq(userId), eq(StaffRole.VENUE_MANAGER), eq(tenantId), any(), any(), any()))
                .thenReturn(new AccessToken("new.jwt.access", UUID.randomUUID().toString(), BASE_TIME.plus(Duration.ofMinutes(15))));

        LoginResponse response = refreshTokenService.rotate(rawToken);

        // Verify tokens
        assertThat(response.accessToken()).isEqualTo("new.jwt.access");
        assertThat(response.refreshToken()).isNotBlank().isNotEqualTo(rawToken);
        assertThat(response.user().role()).isEqualTo(StaffRole.VENUE_MANAGER);

        // Verify old token marked revoked with replacement
        assertThat(existingToken.getRevokedAt()).isEqualTo(BASE_TIME.atOffset(ZoneOffset.UTC));
        assertThat(existingToken.getReplacedByTokenId()).isNotNull();

        // Verify new token persisted in same family
        ArgumentCaptor<RefreshToken> savedCaptor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository, times(2)).save(savedCaptor.capture());
        RefreshToken newSavedToken = savedCaptor.getAllValues().get(0);

        assertThat(newSavedToken.getTokenFamily()).isEqualTo(familyId); // Same family!
        assertThat(newSavedToken.getUserId()).isEqualTo(userId);
        assertThat(newSavedToken.getTokenHash()).isEqualTo(TokenHashUtil.sha256Hex(response.refreshToken()));
        assertThat(newSavedToken.getExpiresAt()).isEqualTo(BASE_TIME.plus(Duration.ofDays(7)).atOffset(ZoneOffset.UTC));
    }

    @Test
    void testRotate_ReuseDetected_RevokesFamilyAndThrows_T31() {
        String rawToken = "already-revoked-token";
        String tokenHash = TokenHashUtil.sha256Hex(rawToken);

        UUID familyId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        RefreshToken revokedToken = RefreshToken.builder()
                .id(UUID.randomUUID())
                .tokenHash(tokenHash)
                .tokenFamily(familyId)
                .userId(userId)
                .expiresAt(BASE_TIME.plus(Duration.ofDays(7)).atOffset(ZoneOffset.UTC))
                .revokedAt(BASE_TIME.minus(Duration.ofHours(1)).atOffset(ZoneOffset.UTC)) // Already revoked!
                .build();

        when(refreshTokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(revokedToken));

        assertThatThrownBy(() -> refreshTokenService.rotate(rawToken))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getErrorCode()).isEqualTo(ErrorCode.AUTH_REFRESH_REUSE_DETECTED));

        // Verify family revocation was invoked immediately
        verify(refreshTokenRepository).revokeEntireFamily(eq(familyId), eq(BASE_TIME.atOffset(ZoneOffset.UTC)));

        // Verify audit event
        ArgumentCaptor<AuditEvent> auditCaptor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditLogService).record(auditCaptor.capture());
        assertThat(auditCaptor.getValue().eventType()).isEqualTo(AuditEventType.AUTH_REFRESH_REUSE_DETECTED);
        assertThat(auditCaptor.getValue().outcome()).isEqualTo("FAILURE");

        // Verify no new token was saved
        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    void testRotate_ExpiredToken_ThrowsAuthTokenExpired_T32() {
        String rawToken = "expired-token";
        String tokenHash = TokenHashUtil.sha256Hex(rawToken);

        RefreshToken expiredToken = RefreshToken.builder()
                .id(UUID.randomUUID())
                .tokenHash(tokenHash)
                .tokenFamily(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .expiresAt(BASE_TIME.minus(Duration.ofSeconds(1)).atOffset(ZoneOffset.UTC)) // Expired!
                .build();

        when(refreshTokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(expiredToken));

        assertThatThrownBy(() -> refreshTokenService.rotate(rawToken))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getErrorCode()).isEqualTo(ErrorCode.AUTH_TOKEN_EXPIRED));

        verify(refreshTokenRepository, never()).revokeEntireFamily(any(), any());
    }

    @Test
    void testRotate_UnknownToken_ThrowsAuthTokenInvalid_T33() {
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> refreshTokenService.rotate("garbage-token"))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getErrorCode()).isEqualTo(ErrorCode.AUTH_TOKEN_INVALID));
    }

    @Test
    void testRotate_RoleChangedInDatabase_ReflectsNewRole_T35() {
        String rawToken = "token-for-promoted-user";
        String tokenHash = TokenHashUtil.sha256Hex(rawToken);

        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();

        RefreshToken token = RefreshToken.builder()
                .id(UUID.randomUUID())
                .tokenHash(tokenHash)
                .tokenFamily(UUID.randomUUID())
                .userId(userId)
                .expiresAt(BASE_TIME.plus(Duration.ofDays(7)).atOffset(ZoneOffset.UTC))
                .build();

        // User role was updated to TENANT_ADMIN in DB since login
        AppUser user = AppUser.builder()
                .tenantId(tenantId)
                .role(StaffRole.TENANT_ADMIN.name())
                .status(UserStatus.ACTIVE.name())
                .available(true)
                .displayName("New Admin")
                .build();
        user.setId(userId);

        when(refreshTokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(token));
        when(appUserRepository.findById(userId)).thenReturn(Optional.of(user));
        when(staffJwtService.issueToken(eq(userId), eq(StaffRole.TENANT_ADMIN), eq(tenantId), any(), any(), any()))
                .thenReturn(new AccessToken("jwt-with-new-role", "jti-1", BASE_TIME.plus(Duration.ofMinutes(15))));

        LoginResponse response = refreshTokenService.rotate(rawToken);

        assertThat(response.user().role()).isEqualTo(StaffRole.TENANT_ADMIN);
        verify(staffJwtService).issueToken(eq(userId), eq(StaffRole.TENANT_ADMIN), eq(tenantId), any(), any(), any());
    }

    @Test
    void testRotate_UserDisabledOrLockedMeanwhile_Refused_T36() {
        String rawToken = "token-for-locked-user";
        String tokenHash = TokenHashUtil.sha256Hex(rawToken);

        UUID userId = UUID.randomUUID();
        RefreshToken token = RefreshToken.builder()
                .id(UUID.randomUUID())
                .tokenHash(tokenHash)
                .tokenFamily(UUID.randomUUID())
                .userId(userId)
                .expiresAt(BASE_TIME.plus(Duration.ofDays(7)).atOffset(ZoneOffset.UTC))
                .build();

        AppUser user = AppUser.builder()
                .status(UserStatus.ACTIVE.name())
                .available(true)
                .build();
        user.setId(userId);

        when(refreshTokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(token));
        when(appUserRepository.findById(userId)).thenReturn(Optional.of(user));
        doThrow(new ApiException(ErrorCode.AUTH_ACCOUNT_LOCKED)).when(loginAttemptService).checkLockout(user);

        assertThatThrownBy(() -> refreshTokenService.rotate(rawToken))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getErrorCode()).isEqualTo(ErrorCode.AUTH_ACCOUNT_LOCKED));
    }

    private static class MutableClock extends Clock {
        private Instant instant;

        public MutableClock(Instant initial) {
            this.instant = initial;
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
            return instant;
        }
    }
}

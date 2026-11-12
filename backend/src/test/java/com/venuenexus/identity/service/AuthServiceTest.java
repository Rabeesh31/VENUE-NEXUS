package com.venuenexus.identity.service;

import com.venuenexus.audit.model.AuditEvent;
import com.venuenexus.audit.model.AuditEventType;
import com.venuenexus.audit.service.AuditLogService;
import com.venuenexus.common.error.ApiException;
import com.venuenexus.common.error.ErrorCode;
import com.venuenexus.identity.api.LoginRequest;
import com.venuenexus.identity.api.LoginResponse;
import com.venuenexus.identity.entity.AppUser;
import com.venuenexus.identity.entity.RefreshToken;
import com.venuenexus.identity.repository.RefreshTokenRepository;
import com.venuenexus.security.StaffRole;
import com.venuenexus.security.jwt.StaffJwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private AuthenticationLookupService lookupService;

    @Mock
    private LoginAttemptService loginAttemptService;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private AuditLogService auditLogService;

    private PasswordVerifier passwordVerifier;
    private StaffJwtService staffJwtService;
    private BCryptPasswordEncoder passwordEncoder;
    private MutableClock testClock;
    private AuthService authService;

    private static final String SECRET = "secure-secret-key-at-least-32-bytes-long-for-test!";
    private static final Instant BASE_TIME = Instant.parse("2026-11-07T12:00:00Z");

    @BeforeEach
    void setUp() {
        testClock = new MutableClock(BASE_TIME);
        passwordEncoder = new BCryptPasswordEncoder(10);
        passwordVerifier = new PasswordVerifier(passwordEncoder);
        passwordVerifier.initDummyHash();

        staffJwtService = new StaffJwtService(
                SECRET,
                "venue-nexus",
                "venue-nexus-staff",
                Duration.ofMinutes(15),
                Duration.ZERO,
                testClock
        );

        authService = new AuthService(
                lookupService,
                loginAttemptService,
                passwordVerifier,
                staffJwtService,
                refreshTokenRepository,
                auditLogService,
                testClock,
                Duration.ofDays(7)
        );
    }

    @Test
    void testLogin_Success_T01_and_T14() {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        String rawPassword = "CorrectPassword123!";

        AppUser user = AppUser.builder()
                .email("manager@venuenexus.com")
                .passwordHash(passwordEncoder.encode(rawPassword))
                .displayName("Jane Manager")
                .role("VENUE_MANAGER")
                .status("ACTIVE")
                .available(true)
                .tenantId(tenantId)
                .build();
        user.setId(userId);

        when(lookupService.findAndValidateUser("manager@venuenexus.com")).thenReturn(Optional.of(user));

        LoginRequest request = new LoginRequest("manager@venuenexus.com", rawPassword);
        LoginResponse response = authService.login(request);

        // Verify tokens
        assertThat(response.accessToken()).isNotBlank();
        assertThat(response.refreshToken()).isNotBlank();
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.expiresIn()).isEqualTo(900);

        // Verify user summary
        assertThat(response.user().id()).isEqualTo(userId);
        assertThat(response.user().displayName()).isEqualTo("Jane Manager");
        assertThat(response.user().role()).isEqualTo(StaffRole.VENUE_MANAGER);
        assertThat(response.user().tenantId()).isEqualTo(tenantId);

        // Verify login success hooks
        verify(loginAttemptService).checkLockout(user);
        verify(loginAttemptService).recordSuccess(user);

        // Verify audit event
        ArgumentCaptor<AuditEvent> auditCaptor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditLogService).record(auditCaptor.capture());
        assertThat(auditCaptor.getValue().eventType()).isEqualTo(AuditEventType.AUTH_LOGIN);
        assertThat(auditCaptor.getValue().outcome()).isEqualTo("SUCCESS");

        // Verify T-14: Refresh token persisted ONLY as SHA-256 hash
        ArgumentCaptor<RefreshToken> tokenCaptor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(tokenCaptor.capture());
        RefreshToken savedToken = tokenCaptor.getValue();

        assertThat(savedToken.getTokenHash()).isEqualTo(TokenHashUtil.sha256Hex(response.refreshToken()));
        assertThat(savedToken.getTokenHash()).isNotEqualTo(response.refreshToken()); // Plaintext NOT stored
        assertThat(savedToken.getTokenFamily()).isNotNull();
        assertThat(savedToken.getUserId()).isEqualTo(userId);
        assertThat(savedToken.getExpiresAt()).isEqualTo(BASE_TIME.plus(Duration.ofDays(7)).atOffset(ZoneOffset.UTC));
    }

    @Test
    void testLogin_UnknownEmail_T03() {
        when(lookupService.findAndValidateUser("unknown@venuenexus.com")).thenReturn(Optional.empty());

        LoginRequest request = new LoginRequest("unknown@venuenexus.com", "anyPassword");

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException apiEx = (ApiException) ex;
                    assertThat(apiEx.getErrorCode()).isEqualTo(ErrorCode.AUTH_INVALID_CREDENTIALS);
                });

        assertThat(passwordVerifier.wasDummyHashInvoked()).isTrue();
        verifyNoInteractions(loginAttemptService);
        verifyNoInteractions(refreshTokenRepository);
        verifyNoInteractions(auditLogService); // D-P3-7: No audit for unknown email
    }

    @Test
    void testLogin_WrongPassword_T02() {
        AppUser user = AppUser.builder()
                .email("staff@venuenexus.com")
                .passwordHash(passwordEncoder.encode("RealPassword"))
                .tenantId(UUID.randomUUID())
                .role("STAFF")
                .status("ACTIVE")
                .available(true)
                .build();
        user.setId(UUID.randomUUID());

        when(lookupService.findAndValidateUser("staff@venuenexus.com")).thenReturn(Optional.of(user));

        LoginRequest request = new LoginRequest("staff@venuenexus.com", "WrongPassword");

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getErrorCode()).isEqualTo(ErrorCode.AUTH_INVALID_CREDENTIALS));

        verify(loginAttemptService).checkLockout(user);
        verify(loginAttemptService).recordFailedAttempt(user);
        verify(loginAttemptService, never()).recordSuccess(any());
        verifyNoInteractions(refreshTokenRepository);

        ArgumentCaptor<AuditEvent> auditCaptor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditLogService).record(auditCaptor.capture());
        assertThat(auditCaptor.getValue().eventType()).isEqualTo(AuditEventType.AUTH_LOGIN_FAILED);
        assertThat(auditCaptor.getValue().outcome()).isEqualTo("FAILURE");
    }

    @Test
    void testLogin_AccountLocked_T07() {
        AppUser user = AppUser.builder()
                .email("locked@venuenexus.com")
                .passwordHash(passwordEncoder.encode("Password123"))
                .tenantId(UUID.randomUUID())
                .role("STAFF")
                .status("ACTIVE")
                .available(true)
                .build();
        user.setId(UUID.randomUUID());

        when(lookupService.findAndValidateUser("locked@venuenexus.com")).thenReturn(Optional.of(user));
        doThrow(new ApiException(ErrorCode.AUTH_ACCOUNT_LOCKED))
                .when(loginAttemptService).checkLockout(user);

        LoginRequest request = new LoginRequest("locked@venuenexus.com", "Password123");

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getErrorCode()).isEqualTo(ErrorCode.AUTH_ACCOUNT_LOCKED));

        // Verifies lockout checked before credentials verification
        verify(loginAttemptService).checkLockout(user);
        verify(loginAttemptService, never()).recordFailedAttempt(any());
        verify(loginAttemptService, never()).recordSuccess(any());
        verifyNoInteractions(refreshTokenRepository);
    }

    @Test
    void testLogin_BlankInput_T06() {
        assertThatThrownBy(() -> authService.login(new LoginRequest("  ", "pass")))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getErrorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED));

        assertThatThrownBy(() -> authService.login(new LoginRequest("user@example.com", "  ")))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getErrorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED));
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

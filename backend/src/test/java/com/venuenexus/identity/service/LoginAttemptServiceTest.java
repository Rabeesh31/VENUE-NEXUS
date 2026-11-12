package com.venuenexus.identity.service;

import com.venuenexus.audit.model.AuditEvent;
import com.venuenexus.audit.model.AuditEventType;
import com.venuenexus.audit.service.AuditLogService;
import com.venuenexus.common.error.ApiException;
import com.venuenexus.common.error.ErrorCode;
import com.venuenexus.identity.entity.AppUser;
import com.venuenexus.identity.repository.AppUserRepository;
import com.venuenexus.security.StaffRole;
import com.venuenexus.security.context.ScopeContext;
import com.venuenexus.security.context.TenantContext;
import org.junit.jupiter.api.AfterEach;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LoginAttemptServiceTest {

    @Mock
    private AppUserRepository appUserRepository;

    @Mock
    private AuditLogService auditLogService;

    private MutableTestClock testClock;
    private LoginAttemptService loginAttemptService;
    private AccountUnlockService accountUnlockService;

    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final Duration LOCKOUT_DURATION = Duration.ofMinutes(15);
    private static final Instant BASE_TIME = Instant.parse("2026-11-07T10:00:00Z");

    @BeforeEach
    void setUp() {
        testClock = new MutableTestClock(BASE_TIME);
        loginAttemptService = new LoginAttemptService(
                appUserRepository,
                auditLogService,
                testClock,
                MAX_FAILED_ATTEMPTS,
                LOCKOUT_DURATION
        );
        accountUnlockService = new AccountUnlockService(
                appUserRepository,
                auditLogService,
                testClock
        );
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        ScopeContext.clear();
    }

    @Test
    void testCheckLockout_NotLocked_DoesNotThrow() {
        AppUser user = createTestUser();
        user.setLockedUntil(null);

        // Does not throw
        loginAttemptService.checkLockout(user);
    }

    @Test
    void testCheckLockout_CurrentlyLocked_ThrowsAuthAccountLocked() {
        AppUser user = createTestUser();
        user.setLockedUntil(BASE_TIME.plus(Duration.ofMinutes(10)).atOffset(ZoneOffset.UTC));

        assertThatThrownBy(() -> loginAttemptService.checkLockout(user))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getErrorCode()).isEqualTo(ErrorCode.AUTH_ACCOUNT_LOCKED));
    }

    @Test
    void testCheckLockout_ExpiredLock_DoesNotThrow() {
        AppUser user = createTestUser();
        // Lock expired 1 minute ago
        user.setLockedUntil(BASE_TIME.minus(Duration.ofMinutes(1)).atOffset(ZoneOffset.UTC));

        // Does not throw because lock is in the past
        loginAttemptService.checkLockout(user);
    }

    @Test
    void testRecordFailedAttempt_UntilThreshold_LocksAccountAndAudits() {
        AppUser user = createTestUser();
        when(appUserRepository.save(any(AppUser.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // 4 attempts -> not locked yet
        for (int i = 1; i <= 4; i++) {
            boolean locked = loginAttemptService.recordFailedAttempt(user);
            assertThat(locked).isFalse();
            assertThat(user.getFailedLoginCount()).isEqualTo(i);
            assertThat(user.getLockedUntil()).isNull();
        }
        verify(auditLogService, never()).record(any());

        // 5th attempt -> triggers lockout
        boolean locked = loginAttemptService.recordFailedAttempt(user);
        assertThat(locked).isTrue();
        assertThat(user.getFailedLoginCount()).isEqualTo(5);
        assertThat(user.getLockedUntil()).isEqualTo(BASE_TIME.plus(LOCKOUT_DURATION).atOffset(ZoneOffset.UTC));

        // Verify audit event AUTH_LOGIN_FAILED
        ArgumentCaptor<AuditEvent> eventCaptor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditLogService).record(eventCaptor.capture());
        AuditEvent event = eventCaptor.getValue();
        assertThat(event.eventType()).isEqualTo(AuditEventType.AUTH_LOGIN_FAILED);
        assertThat(event.userId()).isEqualTo(user.getId());
        assertThat(event.tenantId()).isEqualTo(user.getTenantId());
        assertThat(event.details().get("action")).isEqualTo("ACCOUNT_LOCKED");
    }

    @Test
    void testRecordSuccess_ClearsFailedAttemptsAndLock() {
        AppUser user = createTestUser();
        user.setFailedLoginCount(3);
        user.setLockedUntil(BASE_TIME.plus(Duration.ofMinutes(5)).atOffset(ZoneOffset.UTC));
        when(appUserRepository.save(any(AppUser.class))).thenAnswer(invocation -> invocation.getArgument(0));

        loginAttemptService.recordSuccess(user);

        assertThat(user.getFailedLoginCount()).isZero();
        assertThat(user.getLockedUntil()).isNull();
        assertThat(user.getLastLoginAt()).isEqualTo(BASE_TIME.atOffset(ZoneOffset.UTC));
        assertThat(user.getUpdatedAt()).isEqualTo(BASE_TIME.atOffset(ZoneOffset.UTC));
    }

    @Test
    void testLockExpiration_UsingClockAdvance() {
        AppUser user = createTestUser();
        when(appUserRepository.save(any(AppUser.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Lock user at BASE_TIME
        user.setFailedLoginCount(5);
        user.setLockedUntil(BASE_TIME.plus(LOCKOUT_DURATION).atOffset(ZoneOffset.UTC)); // Locks until 10:15:00

        // At 10:10:00 (during lock duration), still locked
        testClock.setInstant(BASE_TIME.plus(Duration.ofMinutes(10)));
        assertThatThrownBy(() -> loginAttemptService.checkLockout(user))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getErrorCode()).isEqualTo(ErrorCode.AUTH_ACCOUNT_LOCKED));

        // Advance clock to 10:15:01 (1 second past lock duration)
        testClock.setInstant(BASE_TIME.plus(LOCKOUT_DURATION).plusSeconds(1));
        // Check passes without throwing
        loginAttemptService.checkLockout(user);

        // Subsequent login success resets everything
        loginAttemptService.recordSuccess(user);
        assertThat(user.getFailedLoginCount()).isZero();
        assertThat(user.getLockedUntil()).isNull();
    }

    @Test
    void testAdminUnlock_TenantAdmin_Success() {
        UUID tenantId = UUID.randomUUID();
        TenantContext.setTenantId(tenantId);
        ScopeContext.set(new ScopeContext(StaffRole.TENANT_ADMIN, tenantId, null, null, null));

        AppUser user = createTestUser();
        user.setTenantId(tenantId);
        user.setFailedLoginCount(5);
        user.setLockedUntil(BASE_TIME.plus(Duration.ofMinutes(15)).atOffset(ZoneOffset.UTC));

        when(appUserRepository.findByTenantIdAndId(tenantId, user.getId())).thenReturn(Optional.of(user));
        when(appUserRepository.save(any(AppUser.class))).thenAnswer(invocation -> invocation.getArgument(0));

        accountUnlockService.unlock(user.getId());

        assertThat(user.getFailedLoginCount()).isZero();
        assertThat(user.getLockedUntil()).isNull();
        verify(appUserRepository).save(user);
    }

    @Test
    void testAdminUnlock_WrongTenantAdmin_ReturnsNotFound404() {
        UUID callerTenantId = UUID.randomUUID();

        TenantContext.setTenantId(callerTenantId);
        ScopeContext.set(new ScopeContext(StaffRole.TENANT_ADMIN, callerTenantId, null, null, null));

        UUID targetUserId = UUID.randomUUID();
        when(appUserRepository.findByTenantIdAndId(callerTenantId, targetUserId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> accountUnlockService.unlock(targetUserId))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getErrorCode()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));

        verify(appUserRepository, never()).save(any());
    }

    @Test
    void testAdminUnlock_PlatformAdmin_CanUnlockAnyUser() {
        ScopeContext.set(new ScopeContext(StaffRole.PLATFORM_ADMIN, null, null, null, null));

        AppUser user = createTestUser();
        user.setFailedLoginCount(5);
        user.setLockedUntil(BASE_TIME.plus(Duration.ofMinutes(15)).atOffset(ZoneOffset.UTC));

        when(appUserRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(appUserRepository.save(any(AppUser.class))).thenAnswer(invocation -> invocation.getArgument(0));

        accountUnlockService.unlock(user.getId());

        assertThat(user.getFailedLoginCount()).isZero();
        assertThat(user.getLockedUntil()).isNull();
    }

    @Test
    void testConcurrentFailedAttempts_RaceSafeThreshold() throws InterruptedException {
        AppUser user = createTestUser();
        when(appUserRepository.save(any(AppUser.class))).thenAnswer(invocation -> invocation.getArgument(0));

        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicInteger lockedCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    latch.await();
                    synchronized (user) {
                        if (loginAttemptService.recordFailedAttempt(user)) {
                            lockedCount.incrementAndGet();
                        }
                    }
                } catch (InterruptedException ignored) {
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        latch.countDown();
        doneLatch.await();
        executor.shutdown();

        assertThat(user.getFailedLoginCount()).isEqualTo(threadCount);
        assertThat(user.getLockedUntil()).isNotNull();
        assertThat(lockedCount.get()).isGreaterThanOrEqualTo(1);
    }

    private AppUser createTestUser() {
        AppUser user = AppUser.builder()
                .email("staff@venuenexus.com")
                .tenantId(UUID.randomUUID())
                .role("STAFF")
                .status("ACTIVE")
                .available(true)
                .failedLoginCount(0)
                .build();
        user.setId(UUID.randomUUID());
        return user;
    }

    /**
     * Mutable Clock helper for advancing time in tests without Thread.sleep.
     */
    private static class MutableTestClock extends Clock {
        private Instant instant;

        public MutableTestClock(Instant initial) {
            this.instant = initial;
        }

        public void setInstant(Instant instant) {
            this.instant = instant;
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

package com.venuenexus.identity.service;

import com.venuenexus.audit.model.AuditEvent;
import com.venuenexus.audit.model.AuditEventType;
import com.venuenexus.audit.service.AuditLogService;
import com.venuenexus.common.error.ApiException;
import com.venuenexus.common.error.ErrorCode;
import com.venuenexus.common.filter.TraceIdFilter;
import com.venuenexus.identity.entity.AppUser;
import com.venuenexus.identity.repository.AppUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;

/**
 * Service for managing staff failed login attempts, automatic lockout policies,
 * and lock expiration.
 *
 * <p><strong>CRITICAL SECURITY INVARIANT:</strong>
 * Consecutive failed login attempts trigger an automatic account lockout for a configured duration
 * (default 15 minutes after 5 failures). During the lockout window, requests fail immediately
 * with {@link ErrorCode#AUTH_ACCOUNT_LOCKED} before any password verification occurs.
 */
@Service
@Transactional
public class LoginAttemptService {

    private static final Logger log = LoggerFactory.getLogger(LoginAttemptService.class);

    private final AppUserRepository appUserRepository;
    private final AuditLogService auditLogService;
    private final AuthMetricsService authMetricsService;
    private final Clock clock;
    private final int maxFailedAttempts;
    private final Duration lockoutDuration;

    @org.springframework.beans.factory.annotation.Autowired
    public LoginAttemptService(
            AppUserRepository appUserRepository,
            AuditLogService auditLogService,
            Clock clock,
            @Value("${security.login.max-failed-attempts:5}") int maxFailedAttempts,
            @Value("${security.login.lockout-duration:PT15M}") Duration lockoutDuration,
            AuthMetricsService authMetricsService) {
        this.appUserRepository = appUserRepository;
        this.auditLogService = auditLogService;
        this.clock = clock;
        this.maxFailedAttempts = maxFailedAttempts;
        this.lockoutDuration = lockoutDuration;
        this.authMetricsService = authMetricsService;
    }

    public LoginAttemptService(
            AppUserRepository appUserRepository,
            AuditLogService auditLogService,
            Clock clock,
            int maxFailedAttempts,
            Duration lockoutDuration) {
        this(appUserRepository, auditLogService, clock, maxFailedAttempts, lockoutDuration, null);
    }

    /**
     * Checks if the user is currently locked out.
     *
     * @param user the user attempting to log in
     * @throws ApiException with {@link ErrorCode#AUTH_ACCOUNT_LOCKED} if the account is currently locked
     */
    @Transactional(readOnly = true)
    public void checkLockout(AppUser user) {
        OffsetDateTime lockedUntil = user.getLockedUntil();
        if (lockedUntil != null) {
            Instant now = clock.instant();
            if (lockedUntil.toInstant().isAfter(now)) {
                log.warn("Login rejected for locked account [userId={}, lockedUntil={}]", user.getId(), lockedUntil);
                throw new ApiException(ErrorCode.AUTH_ACCOUNT_LOCKED, ErrorCode.AUTH_ACCOUNT_LOCKED.getDefaultMessage());
            }
            // Lock has expired; access will be allowed and counters reset upon successful password verification
        }
    }

    /**
     * Records a failed login attempt for a known user.
     * Increments the counter and applies a lockout if the threshold is met.
     *
     * @param user the user whose login failed
     * @return true if the account is now locked as a result of this attempt, false otherwise
     */
    public boolean recordFailedAttempt(AppUser user) {
        Instant now = clock.instant();
        int newAttempts = user.getFailedLoginCount() + 1;
        user.setFailedLoginCount(newAttempts);
        user.setUpdatedAt(now.atOffset(ZoneOffset.UTC));

        if (newAttempts >= maxFailedAttempts) {
            OffsetDateTime lockedUntil = now.plus(lockoutDuration).atOffset(ZoneOffset.UTC);
            user.setLockedUntil(lockedUntil);
            appUserRepository.save(user);

            log.warn("Account locked after {} failed attempts [userId={}, lockedUntil={}]", newAttempts, user.getId(), lockedUntil);

            // Audit the lockout event via Phase 1 AuditLogService
            if (auditLogService != null) {
                auditLogService.record(AuditEvent.failure(
                        user.getTenantId(),
                        user.getId(),
                        AuditEventType.AUTH_LOGIN_FAILED,
                        TraceIdFilter.getCurrentTraceId(),
                        "127.0.0.1",
                        Map.of(
                                "action", "ACCOUNT_LOCKED",
                                "failedAttempts", newAttempts,
                                "lockedUntil", lockedUntil.toString()
                        )
                ));
            }

            if (authMetricsService != null) {
                authMetricsService.recordLockout();
            }
            return true;
        } else {
            appUserRepository.save(user);
            return false;
        }
    }

    /**
     * Resets failed login counters and updates the last login timestamp upon successful authentication.
     *
     * @param user the user who successfully authenticated
     */
    public void recordSuccess(AppUser user) {
        Instant now = clock.instant();
        user.setFailedLoginCount(0);
        user.setLockedUntil(null);
        user.setLastLoginAt(now.atOffset(ZoneOffset.UTC));
        user.setUpdatedAt(now.atOffset(ZoneOffset.UTC));
        appUserRepository.save(user);
    }

    public int getMaxFailedAttempts() {
        return maxFailedAttempts;
    }

    public Duration getLockoutDuration() {
        return lockoutDuration;
    }
}

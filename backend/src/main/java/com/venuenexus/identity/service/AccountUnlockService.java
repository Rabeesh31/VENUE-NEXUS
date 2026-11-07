package com.venuenexus.identity.service;

import com.venuenexus.audit.service.AuditLogService;
import com.venuenexus.common.error.ApiException;
import com.venuenexus.common.error.ErrorCode;
import com.venuenexus.identity.entity.AppUser;
import com.venuenexus.identity.repository.AppUserRepository;
import com.venuenexus.security.authz.Roles;
import com.venuenexus.security.context.ScopeContext;
import com.venuenexus.security.context.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

/**
 * Service providing administrator capabilities to manually unlock locked staff accounts.
 *
 * <p><strong>CRITICAL SECURITY INVARIANT:</strong>
 * Tenant administrators can only unlock staff accounts belonging to their own tenant.
 * Attempting to unlock a user from another tenant MUST result in {@link ErrorCode#RESOURCE_NOT_FOUND} (404)
 * to prevent user discovery across tenant boundaries.
 */
@Service
@Transactional
public class AccountUnlockService {

    private static final Logger log = LoggerFactory.getLogger(AccountUnlockService.class);

    private final AppUserRepository appUserRepository;
    private final AuditLogService auditLogService;
    private final Clock clock;

    public AccountUnlockService(AppUserRepository appUserRepository, AuditLogService auditLogService, Clock clock) {
        this.appUserRepository = appUserRepository;
        this.auditLogService = auditLogService;
        this.clock = clock;
    }

    /**
     * Unlocks a locked user account, resetting the failed attempts counter and clearing the lock.
     * Restricted to PLATFORM_ADMIN and TENANT_ADMIN roles.
     *
     * @param userId the ID of the user to unlock
     * @throws ApiException with {@link ErrorCode#RESOURCE_NOT_FOUND} if the user does not exist within the caller's tenant
     */
    @PreAuthorize("hasAnyRole('" + Roles.PLATFORM_ADMIN + "','" + Roles.TENANT_ADMIN + "')")
    public void unlock(UUID userId) {
        if (userId == null) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "User ID is required");
        }

        Optional<ScopeContext> scopeOpt = ScopeContext.current();
        boolean isPlatformAdmin = scopeOpt.map(ScopeContext::isPlatformScoped).orElse(false);

        AppUser user;
        if (isPlatformAdmin) {
            user = appUserRepository.findById(userId)
                    .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "User not found"));
        } else {
            UUID tenantId = TenantContext.requireTenantId();
            user = appUserRepository.findByTenantIdAndId(tenantId, userId)
                    .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "User not found"));
        }

        Instant now = clock.instant();
        user.setFailedLoginCount(0);
        user.setLockedUntil(null);
        user.setUpdatedAt(now.atOffset(ZoneOffset.UTC));
        appUserRepository.save(user);

        log.info("Staff account unlocked by administrator [userId={}, tenantId={}]", user.getId(), user.getTenantId());
    }
}

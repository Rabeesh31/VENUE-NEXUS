package com.venuenexus.identity.service;

import com.venuenexus.common.error.ApiException;
import com.venuenexus.common.error.ErrorCode;
import com.venuenexus.identity.entity.AppUser;
import com.venuenexus.identity.model.UserStatus;
import com.venuenexus.identity.repository.AppUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Optional;

/**
 * Service responsible for pre-authentication user lookup and status validation.
 *
 * <p><strong>CRITICAL SECURITY INVARIANT:</strong>
 * Pre-authentication user lookup by email is the single sanctioned exception to
 * tenant-scoped repository lookups, since the user's tenant is unknown prior to login.
 * Disabled accounts and users belonging to suspended tenants are rejected with generic
 * {@link ErrorCode#AUTH_INVALID_CREDENTIALS} to prevent status enumeration.
 */
@Service
@Transactional(readOnly = true)
public class AuthenticationLookupService {

    private static final Logger log = LoggerFactory.getLogger(AuthenticationLookupService.class);

    private final AppUserRepository appUserRepository;
    private final TenantStatusService tenantStatusService;

    public AuthenticationLookupService(AppUserRepository appUserRepository, TenantStatusService tenantStatusService) {
        this.appUserRepository = appUserRepository;
        this.tenantStatusService = tenantStatusService;
    }

    /**
     * Looks up an AppUser by raw email, validating account and tenant operational status.
     *
     * @param rawEmail the raw email provided in the login request
     * @return an {@link Optional} containing the validated active {@link AppUser}, or empty if not found
     * @throws ApiException if email is blank (VALIDATION_FAILED) or if account/tenant is inactive (AUTH_INVALID_CREDENTIALS)
     */
    public Optional<AppUser> findAndValidateUser(String rawEmail) {
        if (rawEmail == null || rawEmail.trim().isEmpty()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Email is required");
        }

        String normalizedEmail = rawEmail.trim().toLowerCase(Locale.ROOT);
        Optional<AppUser> userOpt = appUserRepository.findByEmailIgnoreCase(normalizedEmail);

        if (userOpt.isEmpty()) {
            return Optional.empty();
        }

        AppUser user = userOpt.get();

        // Account status check (ACTIVE and available required)
        UserStatus status = UserStatus.fromString(user.getStatus());
        if (status != UserStatus.ACTIVE || !user.isAvailable()) {
            log.warn("Login attempt for non-active user [userId={}, status={}, available={}]", user.getId(), user.getStatus(), user.isAvailable());
            throw new ApiException(ErrorCode.AUTH_INVALID_CREDENTIALS, ErrorCode.AUTH_INVALID_CREDENTIALS.getDefaultMessage());
        }

        // Tenant status check (PLATFORM_ADMIN has null tenantId)
        if (user.getTenantId() != null && tenantStatusService.isTenantSuspended(user.getTenantId())) {
            log.warn("Login attempt for user with suspended tenant [userId={}, tenantId={}]", user.getId(), user.getTenantId());
            throw new ApiException(ErrorCode.AUTH_INVALID_CREDENTIALS, ErrorCode.AUTH_INVALID_CREDENTIALS.getDefaultMessage());
        }

        return Optional.of(user);
    }
}

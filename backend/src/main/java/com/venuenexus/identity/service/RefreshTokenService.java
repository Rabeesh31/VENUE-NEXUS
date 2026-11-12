package com.venuenexus.identity.service;

import com.venuenexus.audit.model.AuditEvent;
import com.venuenexus.audit.model.AuditEventType;
import com.venuenexus.audit.service.AuditLogService;
import com.venuenexus.common.error.ApiException;
import com.venuenexus.common.error.ErrorCode;
import com.venuenexus.common.filter.TraceIdFilter;
import com.venuenexus.identity.api.LoginResponse;
import com.venuenexus.identity.api.UserSummaryDto;
import com.venuenexus.identity.entity.AppUser;
import com.venuenexus.identity.entity.RefreshToken;
import com.venuenexus.identity.model.UserStatus;
import com.venuenexus.identity.repository.AppUserRepository;
import com.venuenexus.identity.repository.RefreshTokenRepository;
import com.venuenexus.security.StaffRole;
import com.venuenexus.security.jwt.AccessToken;
import com.venuenexus.security.jwt.StaffJwtService;
import com.venuenexus.security.metrics.AuthMetricsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

/**
 * Service managing refresh token rotation, pessimistic locking, and family reuse detection.
 *
 * <p><strong>CRITICAL SECURITY INVARIANT:</strong>
 * Each refresh token can only be used once. If a previously rotated or revoked refresh token
 * is presented again (indicating token compromise or replay), the ENTIRE token family is
 * immediately revoked and the event is audited. The family revocation write MUST commit
 * even though an {@link ApiException} is thrown.
 */
@Service
public class RefreshTokenService {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenService.class);

    private final RefreshTokenRepository refreshTokenRepository;
    private final AppUserRepository appUserRepository;
    private final StaffJwtService staffJwtService;
    private final AuditLogService auditLogService;
    private final TenantStatusService tenantStatusService;
    private final LoginAttemptService loginAttemptService;
    private final AuthMetricsService authMetricsService;
    private final Clock clock;
    private final Duration refreshTtl;
    private final SecureRandom secureRandom = new SecureRandom();

    @org.springframework.beans.factory.annotation.Autowired
    public RefreshTokenService(
            RefreshTokenRepository refreshTokenRepository,
            AppUserRepository appUserRepository,
            StaffJwtService staffJwtService,
            AuditLogService auditLogService,
            TenantStatusService tenantStatusService,
            LoginAttemptService loginAttemptService,
            AuthMetricsService authMetricsService,
            Clock clock,
            @Value("${security.refresh.ttl:P7D}") Duration refreshTtl) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.appUserRepository = appUserRepository;
        this.staffJwtService = staffJwtService;
        this.auditLogService = auditLogService;
        this.tenantStatusService = tenantStatusService;
        this.loginAttemptService = loginAttemptService;
        this.authMetricsService = authMetricsService != null ? authMetricsService : new AuthMetricsService(java.util.Optional.empty());
        this.clock = clock;
        this.refreshTtl = refreshTtl;
    }

    public RefreshTokenService(
            RefreshTokenRepository refreshTokenRepository,
            AppUserRepository appUserRepository,
            StaffJwtService staffJwtService,
            AuditLogService auditLogService,
            TenantStatusService tenantStatusService,
            LoginAttemptService loginAttemptService,
            Clock clock,
            Duration refreshTtl) {
        this(refreshTokenRepository, appUserRepository, staffJwtService, auditLogService,
                tenantStatusService, loginAttemptService, null, clock, refreshTtl);
    }

    /**
     * Rotates a valid refresh token: revokes the old token with a replacement reference,
     * issues a new refresh token in the same family, and issues a fresh access token.
     * Re-reads the user from the database so updated roles/scopes take effect.
     *
     * @param rawRefreshToken the presented plaintext refresh token
     * @return {@link LoginResponse} containing new tokens and updated user summary
     */
    @Transactional(noRollbackFor = ApiException.class)
    public LoginResponse rotate(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Refresh token is required");
        }

        String presentedHash = TokenHashUtil.sha256Hex(rawRefreshToken);

        RefreshToken token = refreshTokenRepository.findByTokenHash(presentedHash)
                .orElseThrow(() -> {
                    authMetricsService.recordRefresh("invalid");
                    return new ApiException(ErrorCode.AUTH_TOKEN_INVALID, ErrorCode.AUTH_TOKEN_INVALID.getDefaultMessage());
                });

        Instant now = clock.instant();

        // Reuse detection: token was already revoked
        if (token.getRevokedAt() != null) {
            log.warn("Refresh token reuse detected! Revoking family [tokenFamily={}, userId={}]", token.getTokenFamily(), token.getUserId());
            revokeFamily(token.getTokenFamily());

            if (auditLogService != null) {
                // Find user tenant if possible for tenant-scoped audit
                UUID tenantId = appUserRepository.findById(token.getUserId())
                        .map(AppUser::getTenantId)
                        .orElse(null);

                auditLogService.record(AuditEvent.failure(
                        tenantId,
                        token.getUserId(),
                        AuditEventType.AUTH_REFRESH_REUSE_DETECTED,
                        TraceIdFilter.getCurrentTraceId(),
                        "127.0.0.1",
                        Map.of(
                                "action", "REFRESH_REUSE_DETECTED",
                                "tokenFamily", token.getTokenFamily().toString()
                        )
                ));
            }

            authMetricsService.recordRefresh("reuse_detected");
            throw new ApiException(ErrorCode.AUTH_REFRESH_REUSE_DETECTED, ErrorCode.AUTH_REFRESH_REUSE_DETECTED.getDefaultMessage());
        }

        // Expiration check
        if (token.getExpiresAt() != null && token.getExpiresAt().toInstant().isBefore(now)) {
            authMetricsService.recordRefresh("expired");
            throw new ApiException(ErrorCode.AUTH_TOKEN_EXPIRED, ErrorCode.AUTH_TOKEN_EXPIRED.getDefaultMessage());
        }

        // Re-read user from database
        AppUser user = appUserRepository.findById(token.getUserId())
                .orElseThrow(() -> new ApiException(ErrorCode.AUTH_INVALID_CREDENTIALS, ErrorCode.AUTH_INVALID_CREDENTIALS.getDefaultMessage()));

        // Validate user account status
        UserStatus status = UserStatus.fromString(user.getStatus());
        if (status != UserStatus.ACTIVE || !user.isAvailable()) {
            throw new ApiException(ErrorCode.AUTH_INVALID_CREDENTIALS, ErrorCode.AUTH_INVALID_CREDENTIALS.getDefaultMessage());
        }

        // Validate tenant status (PLATFORM_ADMIN has null tenantId)
        if (user.getTenantId() != null && tenantStatusService.isTenantSuspended(user.getTenantId())) {
            throw new ApiException(ErrorCode.AUTH_INVALID_CREDENTIALS, ErrorCode.AUTH_INVALID_CREDENTIALS.getDefaultMessage());
        }

        // Check if user account was locked out
        loginAttemptService.checkLockout(user);

        // Generate new opaque refresh token
        byte[] randomBytes = new byte[32];
        secureRandom.nextBytes(randomBytes);
        String newRawRefreshToken = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
        String newHash = TokenHashUtil.sha256Hex(newRawRefreshToken);

        // Insert new token in same family
        RefreshToken nextToken = RefreshToken.builder()
                .id(UUID.randomUUID())
                .tokenHash(newHash)
                .tokenFamily(token.getTokenFamily())
                .userId(user.getId())
                .expiresAt(now.plus(refreshTtl).atOffset(ZoneOffset.UTC))
                .build();
        RefreshToken saved = refreshTokenRepository.save(nextToken);
        UUID replacedId = (saved != null && saved.getId() != null) ? saved.getId() : nextToken.getId();

        // Mark old token revoked and record replacement
        token.setRevokedAt(now.atOffset(ZoneOffset.UTC));
        token.setReplacedByTokenId(replacedId);
        refreshTokenRepository.save(token);

        StaffRole role = StaffRole.fromString(user.getRole());

        // Issue new access JWT reflecting latest database role/scope
        AccessToken newAccessToken = staffJwtService.issueToken(
                user.getId(),
                role,
                user.getTenantId(),
                user.getOrganisationId(),
                user.getLocationId(),
                user.getDepartmentId()
        );

        UserSummaryDto userSummary = new UserSummaryDto(
                user.getId(),
                user.getDisplayName(),
                role,
                user.getTenantId(),
                user.getOrganisationId(),
                user.getLocationId(),
                user.getDepartmentId()
        );

        authMetricsService.recordRefresh("success");

        return LoginResponse.of(
                newAccessToken.token(),
                newRawRefreshToken,
                staffJwtService.getAccessTtl().toSeconds(),
                userSummary
        );
    }

    /**
     * Revokes all refresh tokens in a given family.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void revokeFamily(UUID tokenFamily) {
        if (tokenFamily != null) {
            refreshTokenRepository.revokeEntireFamily(tokenFamily, clock.instant().atOffset(ZoneOffset.UTC));
        }
    }
}

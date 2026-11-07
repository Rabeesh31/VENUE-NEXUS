package com.venuenexus.identity.service;

import com.venuenexus.audit.model.AuditEvent;
import com.venuenexus.audit.model.AuditEventType;
import com.venuenexus.audit.service.AuditLogService;
import com.venuenexus.common.error.ApiException;
import com.venuenexus.common.error.ErrorCode;
import com.venuenexus.common.filter.TraceIdFilter;
import com.venuenexus.identity.api.LoginRequest;
import com.venuenexus.identity.api.LoginResponse;
import com.venuenexus.identity.api.UserSummaryDto;
import com.venuenexus.identity.entity.AppUser;
import com.venuenexus.identity.entity.RefreshToken;
import com.venuenexus.identity.repository.RefreshTokenRepository;
import com.venuenexus.security.StaffRole;
import com.venuenexus.security.jwt.AccessToken;
import com.venuenexus.security.jwt.StaffJwtService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Service orchestrating staff authentication, access JWT generation,
 * opaque refresh token issuance, and audit tracking.
 */
@Service
@Transactional
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final AuthenticationLookupService lookupService;
    private final LoginAttemptService loginAttemptService;
    private final PasswordVerifier passwordVerifier;
    private final StaffJwtService staffJwtService;
    private final RefreshTokenRepository refreshTokenRepository;
    private final AuditLogService auditLogService;
    private final AuthMetricsService authMetricsService;
    private final Clock clock;
    private final Duration refreshTtl;
    private final SecureRandom secureRandom = new SecureRandom();

    @org.springframework.beans.factory.annotation.Autowired
    public AuthService(
            AuthenticationLookupService lookupService,
            LoginAttemptService loginAttemptService,
            PasswordVerifier passwordVerifier,
            StaffJwtService staffJwtService,
            RefreshTokenRepository refreshTokenRepository,
            AuditLogService auditLogService,
            AuthMetricsService authMetricsService,
            Clock clock,
            @Value("${security.refresh.ttl:P7D}") Duration refreshTtl) {
        this.lookupService = lookupService;
        this.loginAttemptService = loginAttemptService;
        this.passwordVerifier = passwordVerifier;
        this.staffJwtService = staffJwtService;
        this.refreshTokenRepository = refreshTokenRepository;
        this.auditLogService = auditLogService;
        this.authMetricsService = authMetricsService != null ? authMetricsService : new AuthMetricsService(java.util.Optional.empty());
        this.clock = clock;
        this.refreshTtl = refreshTtl;
    }

    public AuthService(
            AuthenticationLookupService lookupService,
            LoginAttemptService loginAttemptService,
            PasswordVerifier passwordVerifier,
            StaffJwtService staffJwtService,
            RefreshTokenRepository refreshTokenRepository,
            AuditLogService auditLogService,
            Clock clock,
            Duration refreshTtl) {
        this(lookupService, loginAttemptService, passwordVerifier, staffJwtService,
                refreshTokenRepository, auditLogService, null, clock, refreshTtl);
    }

    /**
     * Authenticates a staff user by email and password, issuing access and refresh tokens.
     *
     * @param request the login request containing email and password
     * @return {@link LoginResponse} containing tokens and user summary
     */
    public LoginResponse login(LoginRequest request) {
        if (request == null || request.email() == null || request.email().isBlank()
                || request.password() == null || request.password().isBlank()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Email and password are required");
        }

        Optional<AppUser> userOpt = lookupService.findAndValidateUser(request.email());

        // Unknown email: timing equalisation via dummy hash comparison (anti-enumeration)
        if (userOpt.isEmpty()) {
            passwordVerifier.verify(request.password(), null);
            log.warn("Failed login attempt for non-existent user [traceId={}]", TraceIdFilter.getCurrentTraceId());
            // D-P3-7: Do not write audit row for unknown email (no tenant); metric and warning log only
            authMetricsService.recordLogin("unknown_email");
            throw new ApiException(ErrorCode.AUTH_INVALID_CREDENTIALS, ErrorCode.AUTH_INVALID_CREDENTIALS.getDefaultMessage());
        }

        AppUser user = userOpt.get();

        // Check if account is currently locked out before verifying credentials
        try {
            loginAttemptService.checkLockout(user);
        } catch (ApiException ex) {
            authMetricsService.recordLogin("account_locked");
            throw ex;
        }

        // Verify password
        boolean matches = passwordVerifier.verify(request.password(), user.getPasswordHash());
        if (!matches) {
            loginAttemptService.recordFailedAttempt(user);
            authMetricsService.recordLogin("invalid_credentials");
            if (auditLogService != null) {
                auditLogService.record(AuditEvent.failure(
                        user.getTenantId(),
                        user.getId(),
                        AuditEventType.AUTH_LOGIN_FAILED,
                        TraceIdFilter.getCurrentTraceId(),
                        "127.0.0.1",
                        Map.of("reason", "INVALID_CREDENTIALS")
                ));
            }
            throw new ApiException(ErrorCode.AUTH_INVALID_CREDENTIALS, ErrorCode.AUTH_INVALID_CREDENTIALS.getDefaultMessage());
        }

        // Authentication succeeded: reset lockout counters and record timestamp
        loginAttemptService.recordSuccess(user);

        StaffRole role = StaffRole.fromString(user.getRole());

        // Issue access JWT
        AccessToken accessToken = staffJwtService.issueToken(
                user.getId(),
                role,
                user.getTenantId(),
                user.getOrganisationId(),
                user.getLocationId(),
                user.getDepartmentId()
        );

        // Issue opaque refresh token (32 random bytes, base64url encoded)
        byte[] randomBytes = new byte[32];
        secureRandom.nextBytes(randomBytes);
        String rawRefreshToken = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);

        // Store ONLY SHA-256 hash of refresh token
        String tokenHash = TokenHashUtil.sha256Hex(rawRefreshToken);
        UUID familyId = UUID.randomUUID();
        Instant expiresAt = clock.instant().plus(refreshTtl);

        RefreshToken refreshTokenEntity = RefreshToken.builder()
                .tokenHash(tokenHash)
                .tokenFamily(familyId)
                .userId(user.getId())
                .expiresAt(expiresAt.atOffset(ZoneOffset.UTC))
                .build();
        refreshTokenRepository.save(refreshTokenEntity);

        // Audit successful login
        if (auditLogService != null) {
            auditLogService.record(AuditEvent.success(
                    user.getTenantId(),
                    user.getId(),
                    AuditEventType.AUTH_LOGIN,
                    TraceIdFilter.getCurrentTraceId(),
                    "127.0.0.1",
                    Map.of("action", "LOGIN_SUCCESS")
            ));
        }

        authMetricsService.recordLogin("success");

        UserSummaryDto userSummary = new UserSummaryDto(
                user.getId(),
                user.getDisplayName(),
                role,
                user.getTenantId(),
                user.getOrganisationId(),
                user.getLocationId(),
                user.getDepartmentId()
        );

        return LoginResponse.of(
                accessToken.token(),
                rawRefreshToken,
                staffJwtService.getAccessTtl().toSeconds(),
                userSummary
        );
    }
}

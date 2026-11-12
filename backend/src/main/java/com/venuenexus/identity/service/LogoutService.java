package com.venuenexus.identity.service;

import com.venuenexus.identity.entity.RefreshToken;
import com.venuenexus.identity.repository.RefreshTokenRepository;
import com.venuenexus.security.context.CurrentStaff;
import com.venuenexus.security.denylist.TokenDenylist;
import com.venuenexus.security.jwt.StaffJwtService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Service orchestrating staff logout: revoking refresh token families and denylisting access tokens.
 *
 * <p><strong>CRITICAL SECURITY INVARIANT:</strong>
 * Refresh token families are revoked only if they belong to the authenticated caller.
 * The access token's {@code jti} is placed on the Redis denylist with a TTL equal to
 * its remaining lifetime. Logout is idempotent.
 */
@Service
@Transactional
public class LogoutService {

    private static final Logger log = LoggerFactory.getLogger(LogoutService.class);

    private final TokenDenylist tokenDenylist;
    private final RefreshTokenRepository refreshTokenRepository;
    private final RefreshTokenService refreshTokenService;
    private final StaffJwtService staffJwtService;

    public LogoutService(
            TokenDenylist tokenDenylist,
            RefreshTokenRepository refreshTokenRepository,
            RefreshTokenService refreshTokenService,
            StaffJwtService staffJwtService) {
        this.tokenDenylist = tokenDenylist;
        this.refreshTokenRepository = refreshTokenRepository;
        this.refreshTokenService = refreshTokenService;
        this.staffJwtService = staffJwtService;
    }

    /**
     * Executes logout for the current staff member.
     *
     * @param staff the authenticated caller identity
     * @param rawRefreshToken the optional plaintext refresh token to revoke
     */
    public void logout(CurrentStaff staff, String rawRefreshToken) {
        if (staff == null) {
            return;
        }

        // 1. Denylist current access token JTI
        if (staff.tokenId() != null && !staff.tokenId().isBlank()) {
            tokenDenylist.denylist(staff.tokenId(), staffJwtService.getAccessTtl());
        }

        // 2. Revoke refresh token family if provided and owned by the caller
        if (rawRefreshToken != null && !rawRefreshToken.isBlank()) {
            String tokenHash = TokenHashUtil.sha256Hex(rawRefreshToken);
            Optional<RefreshToken> tokenOpt = refreshTokenRepository.findByTokenHash(tokenHash);

            if (tokenOpt.isPresent()) {
                RefreshToken token = tokenOpt.get();
                if (staff.userId().equals(token.getUserId())) {
                    refreshTokenService.revokeFamily(token.getTokenFamily());
                    log.info("Refresh token family revoked on logout [tokenFamily={}, userId={}]", token.getTokenFamily(), staff.userId());
                } else {
                    log.warn("Logout attempt presented refresh token of another user [callerId={}, ownerId={}]",
                            staff.userId(), token.getUserId());
                }
            }
        }
    }
}

package com.venuenexus.identity.service;

import com.venuenexus.identity.entity.RefreshToken;
import com.venuenexus.identity.repository.RefreshTokenRepository;
import com.venuenexus.security.StaffRole;
import com.venuenexus.security.context.CurrentStaff;
import com.venuenexus.security.denylist.TokenDenylist;
import com.venuenexus.security.jwt.StaffJwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LogoutServiceTest {

    @Mock
    private TokenDenylist tokenDenylist;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private RefreshTokenService refreshTokenService;

    @Mock
    private StaffJwtService staffJwtService;

    private LogoutService logoutService;

    @BeforeEach
    void setUp() {
        when(staffJwtService.getAccessTtl()).thenReturn(Duration.ofMinutes(15));
        logoutService = new LogoutService(tokenDenylist, refreshTokenRepository, refreshTokenService, staffJwtService);
    }

    @Test
    void testLogout_Success_T40() {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        String tokenId = "jti-12345";
        CurrentStaff staff = new CurrentStaff(userId, StaffRole.STAFF, tenantId, null, null, null, tokenId);

        String rawRefreshToken = "my-refresh-token";
        String tokenHash = TokenHashUtil.sha256Hex(rawRefreshToken);
        UUID familyId = UUID.randomUUID();

        RefreshToken token = RefreshToken.builder()
                .id(UUID.randomUUID())
                .tokenHash(tokenHash)
                .tokenFamily(familyId)
                .userId(userId)
                .expiresAt(OffsetDateTime.now().plusSeconds(3600))
                .build();
        when(refreshTokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(token));

        logoutService.logout(staff, rawRefreshToken);

        // Access token JTI was denylisted
        verify(tokenDenylist).denylist(eq(tokenId), eq(Duration.ofMinutes(15)));

        // Refresh token family was revoked
        verify(refreshTokenService).revokeFamily(eq(familyId));
    }

    @Test
    void testLogout_SomeoneElsesRefreshToken_NotRevoked_T44() {
        UUID callerUserId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID(); // Different user
        String tokenId = "jti-caller";
        CurrentStaff staff = new CurrentStaff(callerUserId, StaffRole.STAFF, UUID.randomUUID(), null, null, null, tokenId);

        String rawRefreshToken = "other-users-refresh-token";
        String tokenHash = TokenHashUtil.sha256Hex(rawRefreshToken);
        UUID otherFamilyId = UUID.randomUUID();

        RefreshToken otherUserToken = RefreshToken.builder()
                .id(UUID.randomUUID())
                .tokenHash(tokenHash)
                .tokenFamily(otherFamilyId)
                .userId(otherUserId)
                .expiresAt(OffsetDateTime.now().plusSeconds(3600))
                .build();
        when(refreshTokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(otherUserToken));

        logoutService.logout(staff, rawRefreshToken);

        // Caller's access token is still denylisted
        verify(tokenDenylist).denylist(eq(tokenId), eq(Duration.ofMinutes(15)));

        // Other user's family is NOT revoked!
        verify(refreshTokenService, never()).revokeFamily(any());
    }

    @Test
    void testLogout_Twice_Idempotent_T43() {
        UUID userId = UUID.randomUUID();
        String tokenId = "jti-twice";
        CurrentStaff staff = new CurrentStaff(userId, StaffRole.STAFF, UUID.randomUUID(), null, null, null, tokenId);

        // First logout
        logoutService.logout(staff, null);
        // Second logout
        logoutService.logout(staff, null);

        verify(tokenDenylist, times(2)).denylist(eq(tokenId), eq(Duration.ofMinutes(15)));
    }
}

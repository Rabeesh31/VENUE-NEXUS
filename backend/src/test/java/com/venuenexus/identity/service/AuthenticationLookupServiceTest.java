package com.venuenexus.identity.service;

import com.venuenexus.common.error.ApiException;
import com.venuenexus.common.error.ErrorCode;
import com.venuenexus.identity.entity.AppUser;
import com.venuenexus.identity.repository.AppUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthenticationLookupServiceTest {

    @Mock
    private AppUserRepository appUserRepository;

    private DefaultTenantStatusService tenantStatusService;
    private AuthenticationLookupService lookupService;
    private PasswordVerifier passwordVerifier;
    private BCryptPasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        tenantStatusService = new DefaultTenantStatusService();
        lookupService = new AuthenticationLookupService(appUserRepository, tenantStatusService);
        passwordEncoder = new BCryptPasswordEncoder(10);
        passwordVerifier = new PasswordVerifier(passwordEncoder);
        passwordVerifier.initDummyHash();
    }

    @Test
    void testFindAndValidateUser_Success_CaseAndWhitespaceNormalised() {
        UUID tenantId = UUID.randomUUID();
        AppUser user = AppUser.builder()
                .email("manager@venuenexus.com")
                .passwordHash(passwordEncoder.encode("Secret123!"))
                .displayName("Manager")
                .role("VENUE_MANAGER")
                .status("ACTIVE")
                .available(true)
                .tenantId(tenantId)
                .build();
        user.setId(UUID.randomUUID());

        when(appUserRepository.findByEmailIgnoreCase("manager@venuenexus.com")).thenReturn(Optional.of(user));

        Optional<AppUser> result = lookupService.findAndValidateUser("   Manager@VenueNexus.com   ");

        assertThat(result).isPresent();
        assertThat(result.get().getEmail()).isEqualTo("manager@venuenexus.com");
        verify(appUserRepository).findByEmailIgnoreCase("manager@venuenexus.com");
    }

    @Test
    void testFindAndValidateUser_BlankEmail_ThrowsValidationError() {
        assertThatThrownBy(() -> lookupService.findAndValidateUser("   "))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getErrorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED));

        assertThatThrownBy(() -> lookupService.findAndValidateUser(null))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getErrorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED));

        verifyNoInteractions(appUserRepository);
    }

    @Test
    void testFindAndValidateUser_UnknownEmail_ReturnsEmpty() {
        when(appUserRepository.findByEmailIgnoreCase("unknown@venuenexus.com")).thenReturn(Optional.empty());

        Optional<AppUser> result = lookupService.findAndValidateUser("unknown@venuenexus.com");
        assertThat(result).isEmpty();

        // Exercise timing equalisation
        boolean match = passwordVerifier.verify("anyPassword", null);
        assertThat(match).isFalse();
        assertThat(passwordVerifier.wasDummyHashInvoked()).isTrue();
    }

    @Test
    void testFindAndValidateUser_DisabledAccount_ThrowsInvalidCredentials() {
        AppUser user = AppUser.builder()
                .email("inactive@venuenexus.com")
                .status("DEACTIVATED")
                .available(false)
                .build();
        user.setId(UUID.randomUUID());

        when(appUserRepository.findByEmailIgnoreCase("inactive@venuenexus.com")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> lookupService.findAndValidateUser("inactive@venuenexus.com"))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException apiEx = (ApiException) ex;
                    assertThat(apiEx.getErrorCode()).isEqualTo(ErrorCode.AUTH_INVALID_CREDENTIALS);
                    assertThat(apiEx.getMessage()).isEqualTo(ErrorCode.AUTH_INVALID_CREDENTIALS.getDefaultMessage());
                });
    }

    @Test
    void testFindAndValidateUser_SuspendedTenant_ThrowsInvalidCredentials() {
        UUID tenantId = UUID.randomUUID();
        tenantStatusService.suspendTenant(tenantId);

        AppUser user = AppUser.builder()
                .email("tenantuser@venuenexus.com")
                .status("ACTIVE")
                .available(true)
                .tenantId(tenantId)
                .build();
        user.setId(UUID.randomUUID());

        when(appUserRepository.findByEmailIgnoreCase("tenantuser@venuenexus.com")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> lookupService.findAndValidateUser("tenantuser@venuenexus.com"))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException apiEx = (ApiException) ex;
                    assertThat(apiEx.getErrorCode()).isEqualTo(ErrorCode.AUTH_INVALID_CREDENTIALS);
                });
    }

    @Test
    void testFindAndValidateUser_PlatformAdmin_AllowedWithoutTenant() {
        AppUser user = AppUser.builder()
                .email("admin@venuenexus.com")
                .status("ACTIVE")
                .available(true)
                .role("PLATFORM_ADMIN")
                .tenantId(null) // Platform admin has no tenant
                .build();
        user.setId(UUID.randomUUID());

        when(appUserRepository.findByEmailIgnoreCase("admin@venuenexus.com")).thenReturn(Optional.of(user));

        Optional<AppUser> result = lookupService.findAndValidateUser("admin@venuenexus.com");
        assertThat(result).isPresent();
        assertThat(result.get().getTenantId()).isNull();
    }

    @Test
    void testPasswordVerifier_TimingEqualisationAndMatching() {
        String rawPass = "MySecureP@ss1";
        String hash = passwordEncoder.encode(rawPass);

        // Matching correct password
        assertThat(passwordVerifier.verify(rawPass, hash)).isTrue();

        // Non-matching wrong password
        assertThat(passwordVerifier.verify("wrong", hash)).isFalse();

        // Unknown user (null hash) exercises dummy verification and returns false
        passwordVerifier.resetDummyHashInvoked();
        assertThat(passwordVerifier.verify(rawPass, null)).isFalse();
        assertThat(passwordVerifier.wasDummyHashInvoked()).isTrue();
    }
}

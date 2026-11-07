package com.venuenexus.identity.service;

import com.venuenexus.common.error.ApiException;
import com.venuenexus.common.error.ErrorCode;
import com.venuenexus.identity.dto.CreateStaffUserRequest;
import com.venuenexus.identity.dto.StaffUserResponse;
import com.venuenexus.identity.entity.AppUser;
import com.venuenexus.identity.repository.AppUserRepository;
import com.venuenexus.security.StaffRole;
import com.venuenexus.security.authz.ScopeEnforcer;
import com.venuenexus.security.context.CurrentStaff;
import com.venuenexus.security.context.TenantContext;
import com.venuenexus.venue.repository.DepartmentRepository;
import com.venuenexus.venue.repository.LocationRepository;
import com.venuenexus.venue.repository.OrganisationRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StaffUserManagementServiceTest {

    @Mock
    private AppUserRepository appUserRepository;

    @Mock
    private OrganisationRepository organisationRepository;

    @Mock
    private LocationRepository locationRepository;

    @Mock
    private DepartmentRepository departmentRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private ScopeEnforcer scopeEnforcer;

    private StaffUserManagementService service;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID callerId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new StaffUserManagementService(
                appUserRepository,
                organisationRepository,
                locationRepository,
                departmentRepository,
                passwordEncoder,
                scopeEnforcer
        );
        TenantContext.setTenantId(tenantId);
        CurrentStaff.set(new CurrentStaff(
                callerId,
                StaffRole.TENANT_ADMIN,
                tenantId,
                null,
                null,
                null,
                "token-jti"
        ));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        CurrentStaff.clear();
    }

    @Test
    @DisplayName("Create staff user successfully encodes password and stores active user")
    void testCreateStaffUser_Success() {
        CreateStaffUserRequest request = new CreateStaffUserRequest(
                "chef@restaurant.com",
                "SecretP@ss123",
                "Head Chef",
                "STAFF",
                null,
                null,
                null
        );

        when(appUserRepository.findByEmailIgnoreCase("chef@restaurant.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("SecretP@ss123")).thenReturn("$2a$12$hashedPassword");

        AppUser saved = AppUser.builder()
                .tenantId(tenantId)
                .email("chef@restaurant.com")
                .displayName("Head Chef")
                .role("STAFF")
                .status("ACTIVE")
                .available(true)
                .build();
        saved.setId(UUID.randomUUID());
        when(appUserRepository.save(any(AppUser.class))).thenReturn(saved);

        StaffUserResponse response = service.createStaffUser(request);
        assertThat(response).isNotNull();
        assertThat(response.email()).isEqualTo("chef@restaurant.com");
        assertThat(response.role()).isEqualTo("STAFF");
        assertThat(response.status()).isEqualTo("ACTIVE");
        verify(passwordEncoder).encode("SecretP@ss123");
    }

    @Test
    @DisplayName("Tenant Admin attempting to create Platform Admin fails with ACCESS_DENIED")
    void testCreateStaffUser_TenantAdminCreatesPlatformAdmin_Fails() {
        CreateStaffUserRequest request = new CreateStaffUserRequest(
                "admin@platform.com",
                "SecretP@ss123",
                "Super Admin",
                "PLATFORM_ADMIN",
                null,
                null,
                null
        );

        when(appUserRepository.findByEmailIgnoreCase("admin@platform.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createStaffUser(request))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ACCESS_DENIED);
    }

    @Test
    @DisplayName("Create user with duplicate email fails with CONFLICT_DUPLICATE")
    void testCreateStaffUser_DuplicateEmail_Fails() {
        CreateStaffUserRequest request = new CreateStaffUserRequest(
                "chef@restaurant.com",
                "SecretP@ss123",
                "Head Chef",
                "STAFF",
                null,
                null,
                null
        );

        when(appUserRepository.findByEmailIgnoreCase("chef@restaurant.com")).thenReturn(Optional.of(new AppUser()));

        assertThatThrownBy(() -> service.createStaffUser(request))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.CONFLICT_DUPLICATE);
    }
}


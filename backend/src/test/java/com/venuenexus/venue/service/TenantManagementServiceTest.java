package com.venuenexus.venue.service;

import com.venuenexus.common.error.ApiException;
import com.venuenexus.common.error.ErrorCode;
import com.venuenexus.common.pagination.PageParams;
import com.venuenexus.common.pagination.PageResponse;
import com.venuenexus.venue.dto.CreateTenantRequest;
import com.venuenexus.venue.dto.TenantResponse;
import com.venuenexus.venue.dto.UpdateTenantRequest;
import com.venuenexus.venue.entity.Tenant;
import com.venuenexus.venue.repository.TenantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TenantManagementServiceTest {

    @Mock
    private TenantRepository tenantRepository;

    private TenantManagementService service;

    @BeforeEach
    void setUp() {
        service = new TenantManagementService(tenantRepository);
    }

    @Test
    @DisplayName("Successfully create tenant")
    void testCreateTenant_Success() {
        CreateTenantRequest request = new CreateTenantRequest("Grand Hotel", "{\"theme\": \"dark\"}");
        when(tenantRepository.findByName("Grand Hotel")).thenReturn(Optional.empty());

        Tenant savedTenant = Tenant.builder()
                .name("Grand Hotel")
                .status("ACTIVE")
                .branding("{\"theme\": \"dark\"}")
                .build();
        savedTenant.setId(UUID.randomUUID());

        when(tenantRepository.save(any(Tenant.class))).thenReturn(savedTenant);

        TenantResponse response = service.createTenant(request);

        assertThat(response).isNotNull();
        assertThat(response.name()).isEqualTo("Grand Hotel");
        assertThat(response.status()).isEqualTo("ACTIVE");
        verify(tenantRepository).save(any(Tenant.class));
    }

    @Test
    @DisplayName("Create tenant with duplicate name fails with CONFLICT_DUPLICATE")
    void testCreateTenant_DuplicateName_Fails() {
        CreateTenantRequest request = new CreateTenantRequest("Grand Hotel", null);
        Tenant existing = Tenant.builder().name("Grand Hotel").build();
        when(tenantRepository.findByName("Grand Hotel")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.createTenant(request))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.CONFLICT_DUPLICATE);
    }

    @Test
    @DisplayName("Get tenant by ID returns mapped response")
    void testGetTenantById_Success() {
        UUID id = UUID.randomUUID();
        Tenant tenant = Tenant.builder().name("Boutique Resort").status("ACTIVE").build();
        tenant.setId(id);
        when(tenantRepository.findById(id)).thenReturn(Optional.of(tenant));

        TenantResponse response = service.getTenantById(id);
        assertThat(response.id()).isEqualTo(id);
        assertThat(response.name()).isEqualTo("Boutique Resort");
    }

    @Test
    @DisplayName("Get tenant with unknown ID throws RESOURCE_NOT_FOUND")
    void testGetTenantById_NotFound() {
        UUID id = UUID.randomUUID();
        when(tenantRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getTenantById(id))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.RESOURCE_NOT_FOUND);
    }

    @Test
    @DisplayName("Suspend and reactivate tenant lifecycle transitions")
    void testTenantLifecycle() {
        UUID id = UUID.randomUUID();
        Tenant tenant = Tenant.builder().name("Cafe").status("ACTIVE").build();
        tenant.setId(id);

        when(tenantRepository.findById(id)).thenReturn(Optional.of(tenant));
        when(tenantRepository.save(any(Tenant.class))).thenAnswer(i -> i.getArgument(0));

        TenantResponse suspended = service.suspendTenant(id);
        assertThat(suspended.status()).isEqualTo("SUSPENDED");

        TenantResponse reactivated = service.reactivateTenant(id);
        assertThat(reactivated.status()).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("List tenants with pagination")
    void testListTenants_Pagination() {
        Tenant tenant = Tenant.builder().name("Resort").status("ACTIVE").build();
        tenant.setId(UUID.randomUUID());
        when(tenantRepository.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(tenant)));

        PageParams pageParams = PageParams.of(0, 10, "name", "asc");
        PageResponse<TenantResponse> result = service.listTenants(null, pageParams);

        assertThat(result.items()).hasSize(1);
        assertThat(result.items().get(0).name()).isEqualTo("Resort");
    }
}


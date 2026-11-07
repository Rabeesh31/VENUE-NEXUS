package com.venuenexus.venue.service;

import com.venuenexus.common.error.ApiException;
import com.venuenexus.common.error.ErrorCode;
import com.venuenexus.security.authz.ScopeEnforcer;
import com.venuenexus.security.context.TenantContext;
import com.venuenexus.venue.dto.CreateLocationRequest;
import com.venuenexus.venue.dto.LocationResponse;
import com.venuenexus.venue.entity.Location;
import com.venuenexus.venue.entity.Organisation;
import com.venuenexus.venue.repository.LocationRepository;
import com.venuenexus.venue.repository.OrganisationRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LocationManagementServiceTest {

    @Mock
    private LocationRepository locationRepository;

    @Mock
    private OrganisationRepository organisationRepository;

    @Mock
    private ScopeEnforcer scopeEnforcer;

    private LocationManagementService service;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID orgId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new LocationManagementService(locationRepository, organisationRepository, scopeEnforcer);
        TenantContext.setTenantId(tenantId);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("Create location with non-existent parent organisation fails with INVALID_PARENT_CHILD")
    void testCreateLocation_InvalidOrg_Fails() {
        CreateLocationRequest request = new CreateLocationRequest(orgId, "Downtown Branch", "UTC", "USD", null);
        when(organisationRepository.findByTenantIdAndId(tenantId, orgId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createLocation(request))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_PARENT_CHILD);
    }

    @Test
    @DisplayName("Create location successfully validates organisation and checks uniqueness")
    void testCreateLocation_Success() {
        CreateLocationRequest request = new CreateLocationRequest(orgId, "Downtown Branch", "UTC", "USD", "{}");
        Organisation org = Organisation.builder().name("Retail").tenantId(tenantId).build();
        org.setId(orgId);

        when(organisationRepository.findByTenantIdAndId(tenantId, orgId)).thenReturn(Optional.of(org));
        when(locationRepository.findByTenantIdAndOrganisationIdAndName(tenantId, orgId, "Downtown Branch")).thenReturn(Optional.empty());

        Location saved = Location.builder()
                .tenantId(tenantId)
                .organisationId(orgId)
                .name("Downtown Branch")
                .timezone("UTC")
                .currency("USD")
                .config("{}")
                .build();
        saved.setId(UUID.randomUUID());

        when(locationRepository.save(any(Location.class))).thenReturn(saved);

        LocationResponse response = service.createLocation(request);
        assertThat(response).isNotNull();
        assertThat(response.name()).isEqualTo("Downtown Branch");
        verify(scopeEnforcer).assertCanAccess(orgId, null);
    }

    @Test
    @DisplayName("Cross-tenant location retrieval throws RESOURCE_NOT_FOUND")
    void testGetLocationById_NotFound() {
        UUID locId = UUID.randomUUID();
        when(locationRepository.findByTenantIdAndId(tenantId, locId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getLocationById(locId))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.RESOURCE_NOT_FOUND);
    }
}


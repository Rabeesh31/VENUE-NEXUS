package com.venuenexus.venue.service;

import com.venuenexus.common.error.ApiException;
import com.venuenexus.common.error.ErrorCode;
import com.venuenexus.common.pagination.PageParams;
import com.venuenexus.common.pagination.PageResponse;
import com.venuenexus.security.authz.ScopeEnforcer;
import com.venuenexus.security.context.TenantContext;
import com.venuenexus.venue.dto.CreateOrganisationRequest;
import com.venuenexus.venue.dto.OrganisationResponse;
import com.venuenexus.venue.dto.UpdateOrganisationRequest;
import com.venuenexus.venue.entity.Organisation;
import com.venuenexus.venue.repository.OrganisationRepository;
import org.junit.jupiter.api.AfterEach;
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
class OrganisationManagementServiceTest {

    @Mock
    private OrganisationRepository organisationRepository;

    @Mock
    private ScopeEnforcer scopeEnforcer;

    private OrganisationManagementService service;

    private final UUID tenantId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new OrganisationManagementService(organisationRepository, scopeEnforcer);
        TenantContext.setTenantId(tenantId);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("Create organisation successfully")
    void testCreateOrganisation_Success() {
        CreateOrganisationRequest request = new CreateOrganisationRequest("North Division");
        when(organisationRepository.findByTenantIdAndName(tenantId, "North Division")).thenReturn(Optional.empty());

        Organisation saved = Organisation.builder().name("North Division").status("ACTIVE").tenantId(tenantId).build();
        saved.setId(UUID.randomUUID());
        when(organisationRepository.save(any(Organisation.class))).thenReturn(saved);

        OrganisationResponse response = service.createOrganisation(request);
        assertThat(response).isNotNull();
        assertThat(response.name()).isEqualTo("North Division");
        assertThat(response.tenantId()).isEqualTo(tenantId);
    }

    @Test
    @DisplayName("Duplicate organisation in same tenant throws CONFLICT_DUPLICATE")
    void testCreateOrganisation_Duplicate_Fails() {
        CreateOrganisationRequest request = new CreateOrganisationRequest("North Division");
        Organisation existing = Organisation.builder().name("North Division").tenantId(tenantId).build();
        when(organisationRepository.findByTenantIdAndName(tenantId, "North Division")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.createOrganisation(request))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.CONFLICT_DUPLICATE);
    }

    @Test
    @DisplayName("Cross-tenant organisation lookup throws RESOURCE_NOT_FOUND")
    void testGetOrganisationById_CrossTenant_NotFound() {
        UUID otherOrgId = UUID.randomUUID();
        when(organisationRepository.findByTenantIdAndId(tenantId, otherOrgId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getOrganisationById(otherOrgId))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.RESOURCE_NOT_FOUND);
    }

    @Test
    @DisplayName("Get organisation by ID calls scopeEnforcer")
    void testGetOrganisationById_Success() {
        UUID orgId = UUID.randomUUID();
        Organisation org = Organisation.builder().name("HQ").tenantId(tenantId).status("ACTIVE").build();
        org.setId(orgId);
        when(organisationRepository.findByTenantIdAndId(tenantId, orgId)).thenReturn(Optional.of(org));

        OrganisationResponse response = service.getOrganisationById(orgId);
        assertThat(response.id()).isEqualTo(orgId);
        verify(scopeEnforcer).assertCanAccess(orgId, null);
    }
}


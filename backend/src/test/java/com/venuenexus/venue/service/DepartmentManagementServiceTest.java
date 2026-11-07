package com.venuenexus.venue.service;

import com.venuenexus.common.error.ApiException;
import com.venuenexus.common.error.ErrorCode;
import com.venuenexus.security.authz.ScopeEnforcer;
import com.venuenexus.security.context.TenantContext;
import com.venuenexus.venue.dto.CreateDepartmentRequest;
import com.venuenexus.venue.dto.DepartmentResponse;
import com.venuenexus.venue.entity.Department;
import com.venuenexus.venue.entity.Location;
import com.venuenexus.venue.repository.DepartmentRepository;
import com.venuenexus.venue.repository.LocationRepository;
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
class DepartmentManagementServiceTest {

    @Mock
    private DepartmentRepository departmentRepository;

    @Mock
    private LocationRepository locationRepository;

    @Mock
    private ScopeEnforcer scopeEnforcer;

    private DepartmentManagementService service;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID locationId = UUID.randomUUID();
    private final UUID orgId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new DepartmentManagementService(departmentRepository, locationRepository, scopeEnforcer);
        TenantContext.setTenantId(tenantId);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("Create department successfully")
    void testCreateDepartment_Success() {
        CreateDepartmentRequest request = new CreateDepartmentRequest(locationId, "Main Kitchen", "KITCHEN");

        Location loc = Location.builder().organisationId(orgId).build();
        loc.setId(locationId);
        when(locationRepository.findByTenantIdAndId(tenantId, locationId)).thenReturn(Optional.of(loc));
        when(departmentRepository.findByTenantIdAndLocationIdAndName(tenantId, locationId, "Main Kitchen")).thenReturn(Optional.empty());

        Department saved = Department.builder()
                .tenantId(tenantId)
                .locationId(locationId)
                .name("Main Kitchen")
                .departmentType("KITCHEN")
                .build();
        saved.setId(UUID.randomUUID());
        when(departmentRepository.save(any(Department.class))).thenReturn(saved);

        DepartmentResponse response = service.createDepartment(request);
        assertThat(response.name()).isEqualTo("Main Kitchen");
        assertThat(response.departmentType()).isEqualTo("KITCHEN");
        verify(scopeEnforcer).assertCanAccess(orgId, locationId);
    }

    @Test
    @DisplayName("Create department with duplicate name in same location fails with CONFLICT_DUPLICATE")
    void testCreateDepartment_Duplicate_Fails() {
        CreateDepartmentRequest request = new CreateDepartmentRequest(locationId, "Bar", "BAR");

        Location loc = Location.builder().organisationId(orgId).build();
        loc.setId(locationId);
        when(locationRepository.findByTenantIdAndId(tenantId, locationId)).thenReturn(Optional.of(loc));

        Department existing = Department.builder().name("Bar").build();
        when(departmentRepository.findByTenantIdAndLocationIdAndName(tenantId, locationId, "Bar")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.createDepartment(request))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.CONFLICT_DUPLICATE);
    }
}


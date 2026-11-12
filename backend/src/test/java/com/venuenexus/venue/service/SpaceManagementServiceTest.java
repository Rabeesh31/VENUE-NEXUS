package com.venuenexus.venue.service;

import com.venuenexus.common.error.ApiException;
import com.venuenexus.common.error.ErrorCode;
import com.venuenexus.security.authz.ScopeEnforcer;
import com.venuenexus.security.context.TenantContext;
import com.venuenexus.venue.dto.CreateSpaceRequest;
import com.venuenexus.venue.dto.SpaceResponse;
import com.venuenexus.venue.entity.Location;
import com.venuenexus.venue.entity.Space;
import com.venuenexus.venue.repository.LocationRepository;
import com.venuenexus.venue.repository.SpaceRepository;
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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SpaceManagementServiceTest {

    @Mock
    private SpaceRepository spaceRepository;

    @Mock
    private LocationRepository locationRepository;

    @Mock
    private ScopeEnforcer scopeEnforcer;

    private SpaceManagementService service;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID locationId = UUID.randomUUID();
    private final UUID orgId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new SpaceManagementService(spaceRepository, locationRepository, scopeEnforcer);
        TenantContext.setTenantId(tenantId);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("Create Building without parent space succeeds")
    void testCreateBuilding_Success() {
        CreateSpaceRequest request = new CreateSpaceRequest(locationId, null, "BUILDING", "Tower A");

        Location loc = Location.builder().organisationId(orgId).build();
        loc.setId(locationId);
        when(locationRepository.findByTenantIdAndId(tenantId, locationId)).thenReturn(Optional.of(loc));

        Space saved = Space.builder()
                .tenantId(tenantId)
                .locationId(locationId)
                .spaceType("BUILDING")
                .name("Tower A")
                .build();
        saved.setId(UUID.randomUUID());
        when(spaceRepository.save(any(Space.class))).thenReturn(saved);

        SpaceResponse response = service.createSpace(request);
        assertThat(response.name()).isEqualTo("Tower A");
        assertThat(response.spaceType()).isEqualTo("BUILDING");
    }

    @Test
    @DisplayName("Building with parent space fails with INVALID_PARENT_CHILD")
    void testCreateBuilding_WithParent_Fails() {
        UUID parentId = UUID.randomUUID();
        CreateSpaceRequest request = new CreateSpaceRequest(locationId, parentId, "BUILDING", "Tower B");

        Location loc = Location.builder().organisationId(orgId).build();
        loc.setId(locationId);
        when(locationRepository.findByTenantIdAndId(tenantId, locationId)).thenReturn(Optional.of(loc));

        assertThatThrownBy(() -> service.createSpace(request))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_PARENT_CHILD);
    }

    @Test
    @DisplayName("Floor without parent building fails with INVALID_PARENT_CHILD")
    void testCreateFloor_WithoutParent_Fails() {
        CreateSpaceRequest request = new CreateSpaceRequest(locationId, null, "FLOOR", "Floor 1");

        Location loc = Location.builder().organisationId(orgId).build();
        loc.setId(locationId);
        when(locationRepository.findByTenantIdAndId(tenantId, locationId)).thenReturn(Optional.of(loc));

        assertThatThrownBy(() -> service.createSpace(request))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_PARENT_CHILD);
    }

    @Test
    @DisplayName("Floor with valid parent building succeeds")
    void testCreateFloor_WithBuildingParent_Success() {
        UUID buildingId = UUID.randomUUID();
        CreateSpaceRequest request = new CreateSpaceRequest(locationId, buildingId, "FLOOR", "Floor 1");

        Location loc = Location.builder().organisationId(orgId).build();
        loc.setId(locationId);
        when(locationRepository.findByTenantIdAndId(tenantId, locationId)).thenReturn(Optional.of(loc));

        Space building = Space.builder().tenantId(tenantId).locationId(locationId).spaceType("BUILDING").name("Main Building").build();
        building.setId(buildingId);
        when(spaceRepository.findByTenantIdAndId(tenantId, buildingId)).thenReturn(Optional.of(building));

        Space floor = Space.builder().tenantId(tenantId).locationId(locationId).parentSpaceId(buildingId).spaceType("FLOOR").name("Floor 1").build();
        floor.setId(UUID.randomUUID());
        when(spaceRepository.save(any(Space.class))).thenReturn(floor);

        SpaceResponse response = service.createSpace(request);
        assertThat(response.spaceType()).isEqualTo("FLOOR");
        assertThat(response.parentSpaceId()).isEqualTo(buildingId);
    }
}


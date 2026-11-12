package com.venuenexus.venue.service;

import com.venuenexus.common.error.ApiException;
import com.venuenexus.common.error.ErrorCode;
import com.venuenexus.common.pagination.PageParams;
import com.venuenexus.common.pagination.PageResponse;
import com.venuenexus.security.authz.ScopeEnforcer;
import com.venuenexus.security.context.TenantContext;
import com.venuenexus.venue.dto.CreateSpaceRequest;
import com.venuenexus.venue.dto.SpaceResponse;
import com.venuenexus.venue.dto.UpdateSpaceRequest;
import com.venuenexus.venue.entity.Location;
import com.venuenexus.venue.entity.Space;
import com.venuenexus.venue.repository.LocationRepository;
import com.venuenexus.venue.repository.SpaceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SpaceManagementService {

    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of("name", "spaceType", "createdAt");

    private final SpaceRepository spaceRepository;
    private final LocationRepository locationRepository;
    private final ScopeEnforcer scopeEnforcer;

    @Transactional
    public SpaceResponse createSpace(CreateSpaceRequest request) {
        UUID tenantId = TenantContext.requireTenantId();

        // 1. Verify parent location exists in current tenant
        Location location = locationRepository.findByTenantIdAndId(tenantId, request.locationId())
                .orElseThrow(() -> new ApiException(ErrorCode.INVALID_PARENT_CHILD, "Parent location not found in this tenant"));

        // 2. Scope check on location
        scopeEnforcer.assertCanAccess(location.getOrganisationId(), location.getId());

        // 3. Hierarchy validation: BUILDING -> FLOOR -> ZONE
        String spaceType = request.spaceType().toUpperCase();
        if ("BUILDING".equals(spaceType)) {
            if (request.parentSpaceId() != null) {
                throw new ApiException(ErrorCode.INVALID_PARENT_CHILD, "A BUILDING cannot have a parent space");
            }
        } else if ("FLOOR".equals(spaceType)) {
            if (request.parentSpaceId() == null) {
                throw new ApiException(ErrorCode.INVALID_PARENT_CHILD, "A FLOOR must have a parent BUILDING");
            }
            Space parent = spaceRepository.findByTenantIdAndId(tenantId, request.parentSpaceId())
                    .orElseThrow(() -> new ApiException(ErrorCode.INVALID_PARENT_CHILD, "Parent space not found in this tenant"));
            if (!parent.getLocationId().equals(request.locationId())) {
                throw new ApiException(ErrorCode.INVALID_PARENT_CHILD, "Parent space must belong to the same location");
            }
            if (!"BUILDING".equalsIgnoreCase(parent.getSpaceType())) {
                throw new ApiException(ErrorCode.INVALID_PARENT_CHILD, "Parent space of a FLOOR must be a BUILDING");
            }
        } else if ("ZONE".equals(spaceType)) {
            if (request.parentSpaceId() == null) {
                throw new ApiException(ErrorCode.INVALID_PARENT_CHILD, "A ZONE must have a parent FLOOR");
            }
            Space parent = spaceRepository.findByTenantIdAndId(tenantId, request.parentSpaceId())
                    .orElseThrow(() -> new ApiException(ErrorCode.INVALID_PARENT_CHILD, "Parent space not found in this tenant"));
            if (!parent.getLocationId().equals(request.locationId())) {
                throw new ApiException(ErrorCode.INVALID_PARENT_CHILD, "Parent space must belong to the same location");
            }
            if (!"FLOOR".equalsIgnoreCase(parent.getSpaceType())) {
                throw new ApiException(ErrorCode.INVALID_PARENT_CHILD, "Parent space of a ZONE must be a FLOOR");
            }
        }

        Space space = Space.builder()
                .tenantId(tenantId)
                .locationId(request.locationId())
                .parentSpaceId(request.parentSpaceId())
                .spaceType(spaceType)
                .name(request.name())
                .build();

        Space saved = spaceRepository.save(space);
        return mapToResponse(saved);
    }

    @Transactional(readOnly = true)
    public SpaceResponse getSpaceById(UUID spaceId) {
        UUID tenantId = TenantContext.requireTenantId();
        Space space = spaceRepository.findByTenantIdAndId(tenantId, spaceId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Space not found"));

        Location location = locationRepository.findByTenantIdAndId(tenantId, space.getLocationId())
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Location not found"));

        scopeEnforcer.assertCanAccess(location.getOrganisationId(), location.getId());
        return mapToResponse(space);
    }

    @Transactional(readOnly = true)
    public List<SpaceResponse> listSpacesByLocation(UUID locationId) {
        UUID tenantId = TenantContext.requireTenantId();
        Location location = locationRepository.findByTenantIdAndId(tenantId, locationId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Location not found"));

        scopeEnforcer.assertCanAccess(location.getOrganisationId(), location.getId());

        return spaceRepository.findByTenantIdAndLocationId(tenantId, locationId)
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public PageResponse<SpaceResponse> listSpaces(UUID locationId, String spaceType, PageParams pageParams) {
        UUID tenantId = TenantContext.requireTenantId();
        pageParams.validate(ALLOWED_SORT_FIELDS);
        Pageable pageable = pageParams.toPageable(ALLOWED_SORT_FIELDS);

        Location location = locationRepository.findByTenantIdAndId(tenantId, locationId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Location not found"));

        scopeEnforcer.assertCanAccess(location.getOrganisationId(), location.getId());

        Page<Space> page;
        if (spaceType != null && !spaceType.isBlank()) {
            page = spaceRepository.findByTenantIdAndLocationIdAndSpaceType(tenantId, locationId, spaceType.toUpperCase(), pageable);
        } else {
            page = spaceRepository.findAllByTenantId(tenantId, pageable);
        }

        var filtered = page.getContent().stream()
                .filter(s -> s.getLocationId().equals(locationId))
                .map(this::mapToResponse)
                .toList();

        return PageResponse.of(filtered, page.getNumber(), page.getSize(), filtered.size());
    }

    @Transactional
    public SpaceResponse updateSpace(UUID spaceId, UpdateSpaceRequest request) {
        UUID tenantId = TenantContext.requireTenantId();
        Space space = spaceRepository.findByTenantIdAndId(tenantId, spaceId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Space not found"));

        Location location = locationRepository.findByTenantIdAndId(tenantId, space.getLocationId())
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Location not found"));

        scopeEnforcer.assertCanAccess(location.getOrganisationId(), location.getId());

        space.setName(request.name());
        Space updated = spaceRepository.save(space);
        return mapToResponse(updated);
    }

    private SpaceResponse mapToResponse(Space space) {
        return new SpaceResponse(
                space.getId(),
                space.getTenantId(),
                space.getLocationId(),
                space.getParentSpaceId(),
                space.getSpaceType(),
                space.getName(),
                space.getCreatedAt(),
                space.getUpdatedAt()
        );
    }
}


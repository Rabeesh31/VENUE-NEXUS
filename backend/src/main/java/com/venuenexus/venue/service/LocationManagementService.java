package com.venuenexus.venue.service;

import com.venuenexus.common.error.ApiException;
import com.venuenexus.common.error.ErrorCode;
import com.venuenexus.common.pagination.PageParams;
import com.venuenexus.common.pagination.PageResponse;
import com.venuenexus.security.authz.ScopeEnforcer;
import com.venuenexus.security.context.TenantContext;
import com.venuenexus.venue.dto.CreateLocationRequest;
import com.venuenexus.venue.dto.LocationResponse;
import com.venuenexus.venue.dto.UpdateLocationRequest;
import com.venuenexus.venue.entity.Location;
import com.venuenexus.venue.entity.Organisation;
import com.venuenexus.venue.repository.LocationRepository;
import com.venuenexus.venue.repository.OrganisationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class LocationManagementService {

    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of("name", "createdAt");

    private final LocationRepository locationRepository;
    private final OrganisationRepository organisationRepository;
    private final ScopeEnforcer scopeEnforcer;

    @Transactional
    public LocationResponse createLocation(CreateLocationRequest request) {
        UUID tenantId = TenantContext.requireTenantId();

        // 1. Verify parent organisation exists and belongs to current tenant
        Organisation org = organisationRepository.findByTenantIdAndId(tenantId, request.organisationId())
                .orElseThrow(() -> new ApiException(ErrorCode.INVALID_PARENT_CHILD, "Parent organisation not found in this tenant"));

        // 2. Verify caller has permission to operate within this organisation
        scopeEnforcer.assertCanAccess(org.getId(), null);

        // 3. Uniqueness check for location name within organisation
        if (locationRepository.findByTenantIdAndOrganisationIdAndName(tenantId, request.organisationId(), request.name()).isPresent()) {
            throw new ApiException(ErrorCode.CONFLICT_DUPLICATE, "Location with name '" + request.name() + "' already exists in this organisation");
        }

        Location location = Location.builder()
                .tenantId(tenantId)
                .organisationId(request.organisationId())
                .name(request.name())
                .timezone(request.timezone() != null && !request.timezone().isBlank() ? request.timezone() : "UTC")
                .currency(request.currency() != null && !request.currency().isBlank() ? request.currency() : "USD")
                .config(request.config())
                .build();

        Location saved = locationRepository.save(location);
        return mapToResponse(saved);
    }

    @Transactional(readOnly = true)
    public LocationResponse getLocationById(UUID locationId) {
        UUID tenantId = TenantContext.requireTenantId();
        Location location = locationRepository.findByTenantIdAndId(tenantId, locationId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Location not found"));

        scopeEnforcer.assertCanAccess(location.getOrganisationId(), location.getId());
        return mapToResponse(location);
    }

    @Transactional(readOnly = true)
    public PageResponse<LocationResponse> listLocations(UUID organisationId, PageParams pageParams) {
        UUID tenantId = TenantContext.requireTenantId();
        pageParams.validate(ALLOWED_SORT_FIELDS);
        Pageable pageable = pageParams.toPageable(ALLOWED_SORT_FIELDS);

        // Scope checks
        if (organisationId != null) {
            scopeEnforcer.assertCanAccess(organisationId, null);
        }

        Page<Location> page;
        if (organisationId != null) {
            page = locationRepository.findByTenantIdAndOrganisationId(tenantId, organisationId, pageable);
        } else {
            page = locationRepository.findAllByTenantId(tenantId, pageable);
        }

        var restrictedLoc = scopeEnforcer.restrictedLocationId();
        var restrictedOrg = scopeEnforcer.restrictedOrganisationId();

        var filtered = page.getContent().stream()
                .filter(loc -> {
                    if (restrictedOrg.isPresent() && !loc.getOrganisationId().equals(restrictedOrg.get())) {
                        return false;
                    }
                    if (restrictedLoc.isPresent() && !loc.getId().equals(restrictedLoc.get())) {
                        return false;
                    }
                    return true;
                })
                .map(this::mapToResponse)
                .toList();

        return PageResponse.of(filtered, page.getNumber(), page.getSize(), filtered.size());
    }

    @Transactional
    public LocationResponse updateLocation(UUID locationId, UpdateLocationRequest request) {
        UUID tenantId = TenantContext.requireTenantId();
        Location location = locationRepository.findByTenantIdAndId(tenantId, locationId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Location not found"));

        scopeEnforcer.assertCanAccess(location.getOrganisationId(), location.getId());

        if (!location.getName().equalsIgnoreCase(request.name()) &&
                locationRepository.findByTenantIdAndOrganisationIdAndName(tenantId, location.getOrganisationId(), request.name()).isPresent()) {
            throw new ApiException(ErrorCode.CONFLICT_DUPLICATE, "Location with name '" + request.name() + "' already exists in this organisation");
        }

        location.setName(request.name());
        if (request.timezone() != null && !request.timezone().isBlank()) {
            location.setTimezone(request.timezone());
        }
        if (request.currency() != null && !request.currency().isBlank()) {
            location.setCurrency(request.currency());
        }
        if (request.config() != null) {
            location.setConfig(request.config());
        }

        Location updated = locationRepository.save(location);
        return mapToResponse(updated);
    }

    private LocationResponse mapToResponse(Location loc) {
        return new LocationResponse(
                loc.getId(),
                loc.getTenantId(),
                loc.getOrganisationId(),
                loc.getName(),
                loc.getTimezone(),
                loc.getCurrency(),
                loc.getConfig(),
                loc.getCreatedAt(),
                loc.getUpdatedAt()
        );
    }
}


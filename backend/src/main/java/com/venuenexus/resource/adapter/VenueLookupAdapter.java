package com.venuenexus.resource.adapter;

import com.venuenexus.resource.port.Ports.VenueLookup;
import com.venuenexus.venue.repository.DepartmentRepository;
import com.venuenexus.venue.repository.LocationRepository;
import com.venuenexus.venue.repository.SpaceRepository;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Production adapter linking Phase 5 VenueLookup port to Phase 4 Venue repositories.
 */
@Component
public class VenueLookupAdapter implements VenueLookup {

    private final LocationRepository locationRepository;
    private final SpaceRepository spaceRepository;
    private final DepartmentRepository departmentRepository;

    public VenueLookupAdapter(
            LocationRepository locationRepository,
            SpaceRepository spaceRepository,
            DepartmentRepository departmentRepository) {
        this.locationRepository = locationRepository;
        this.spaceRepository = spaceRepository;
        this.departmentRepository = departmentRepository;
    }

    @Override
    public boolean locationExists(UUID tenantId, UUID locationId) {
        if (tenantId == null || locationId == null) {
            return false;
        }
        return locationRepository.existsByTenantIdAndId(tenantId, locationId);
    }

    @Override
    public boolean spaceBelongsToLocation(UUID tenantId, UUID spaceId, UUID locationId) {
        if (tenantId == null || spaceId == null || locationId == null) {
            return false;
        }
        return spaceRepository.findByTenantIdAndId(tenantId, spaceId)
                .map(s -> locationId.equals(s.getLocationId()))
                .orElse(false);
    }

    @Override
    public boolean departmentBelongsToLocation(UUID tenantId, UUID departmentId, UUID locationId) {
        if (tenantId == null || departmentId == null || locationId == null) {
            return false;
        }
        return departmentRepository.findByTenantIdAndId(tenantId, departmentId)
                .map(d -> locationId.equals(d.getLocationId()))
                .orElse(false);
    }
}

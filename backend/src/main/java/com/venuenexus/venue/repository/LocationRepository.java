package com.venuenexus.venue.repository;

import com.venuenexus.common.repository.TenantScopedRepository;
import com.venuenexus.venue.entity.Location;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Tenant-scoped repository for Location entities.
 */
@Repository
public interface LocationRepository extends TenantScopedRepository<Location, UUID> {

    Optional<Location> findByTenantIdAndOrganisationIdAndName(UUID tenantId, UUID organisationId, String name);

    Page<Location> findByTenantIdAndOrganisationId(UUID tenantId, UUID organisationId, Pageable pageable);
}

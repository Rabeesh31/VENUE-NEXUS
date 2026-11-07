package com.venuenexus.venue.repository;

import com.venuenexus.common.repository.TenantScopedRepository;
import com.venuenexus.venue.entity.Space;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Tenant-scoped repository for Space entities.
 */
@Repository
public interface SpaceRepository extends TenantScopedRepository<Space, UUID> {

    List<Space> findByTenantIdAndLocationId(UUID tenantId, UUID locationId);

    List<Space> findByTenantIdAndParentSpaceId(UUID tenantId, UUID parentSpaceId);

    Page<Space> findByTenantIdAndLocationIdAndSpaceType(UUID tenantId, UUID locationId, String spaceType, Pageable pageable);
}

package com.venuenexus.venue.repository;

import com.venuenexus.common.repository.TenantScopedRepository;
import com.venuenexus.venue.entity.Organisation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Tenant-scoped repository for Organisation entities.
 */
@Repository
public interface OrganisationRepository extends TenantScopedRepository<Organisation, UUID> {

    Optional<Organisation> findByTenantIdAndName(UUID tenantId, String name);

    Page<Organisation> findByTenantIdAndStatus(UUID tenantId, String status, Pageable pageable);
}

package com.venuenexus.resource.repository;

import com.venuenexus.common.repository.TenantScopedRepository;
import com.venuenexus.resource.entity.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Tenant-scoped repository for Physical Resource entities.
 */
@Repository
public interface ResourceRepository extends TenantScopedRepository<Resource, UUID> {

    Optional<Resource> findByTenantIdAndLocationIdAndResourceCode(UUID tenantId, UUID locationId, String resourceCode);

    Page<Resource> findByTenantIdAndLocationId(UUID tenantId, UUID locationId, Pageable pageable);

    Page<Resource> findByTenantIdAndLocationIdAndStatus(UUID tenantId, UUID locationId, String status, Pageable pageable);

    Page<Resource> findByTenantIdAndLocationIdAndDepartmentId(UUID tenantId, UUID locationId, UUID departmentId, Pageable pageable);
}

package com.venuenexus.venue.repository;

import com.venuenexus.common.repository.TenantScopedRepository;
import com.venuenexus.venue.entity.Department;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Tenant-scoped repository for Department entities.
 */
@Repository
public interface DepartmentRepository extends TenantScopedRepository<Department, UUID> {

    Optional<Department> findByTenantIdAndLocationIdAndName(UUID tenantId, UUID locationId, String name);

    Page<Department> findByTenantIdAndLocationId(UUID tenantId, UUID locationId, Pageable pageable);
}

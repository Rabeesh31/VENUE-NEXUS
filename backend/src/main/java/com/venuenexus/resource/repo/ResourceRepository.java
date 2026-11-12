package com.venuenexus.resource.repo;

import com.venuenexus.resource.domain.Resource;
import com.venuenexus.resource.domain.ResourceStatus;
import com.venuenexus.resource.domain.ResourceType;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Tenant-scoped repository for physical resources.
 * Plain findById is hidden on purpose: every lookup takes tenantId.
 * Location-scoped callers pass their allowed location ids; null = whole tenant.
 */
public interface ResourceRepository extends JpaRepository<Resource, UUID> {

    @Override
    @Deprecated
    default Optional<Resource> findById(UUID id) {
        throw new UnsupportedOperationException("Use findByIdAndTenantId");
    }

    Optional<Resource> findByIdAndTenantId(UUID id, UUID tenantId);

    Optional<Resource> findByTenantIdAndLocationIdAndResourceCode(UUID tenantId, UUID locationId, String resourceCode);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM Resource r WHERE r.id = :id AND r.tenantId = :tenantId")
    Optional<Resource> lockByIdAndTenantId(@Param("id") UUID id, @Param("tenantId") UUID tenantId);

    boolean existsByTenantIdAndLocationIdAndResourceCodeIgnoreCase(UUID tenantId, UUID locationId, String resourceCode);

    @Query("""
        SELECT r.resourceCode FROM Resource r
        WHERE r.tenantId = :tenantId AND r.locationId = :locationId AND upper(r.resourceCode) IN :codes
        """)
    List<String> findExistingCodes(@Param("tenantId") UUID tenantId,
                                   @Param("locationId") UUID locationId,
                                   @Param("codes") Collection<String> upperCodes);

    @Query("""
        SELECT r FROM Resource r
        WHERE r.tenantId = :tenantId
          AND (:locationIds IS NULL OR r.locationId IN :locationIds)
          AND (:locationId IS NULL OR r.locationId = :locationId)
          AND (:type IS NULL OR r.type = :type)
          AND (:status IS NULL OR r.status = :status)
        """)
    Page<Resource> search(@Param("tenantId") UUID tenantId,
                          @Param("locationIds") Collection<UUID> allowedLocationIds,
                          @Param("locationId") UUID locationId,
                          @Param("type") ResourceType type,
                          @Param("status") ResourceStatus status,
                          Pageable pageable);
}

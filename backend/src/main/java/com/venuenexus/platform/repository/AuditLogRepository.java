package com.venuenexus.platform.repository;

import com.venuenexus.platform.entity.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository for append-only AuditLog entities.
 */
@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {

    @Query("SELECT MAX(a.tenantSequence) FROM AuditLog a WHERE a.tenantId = :tenantId")
    Optional<Long> findLatestSequenceByTenantId(@Param("tenantId") UUID tenantId);

    @Query("SELECT a FROM AuditLog a WHERE a.tenantId = :tenantId AND a.tenantSequence = (SELECT MAX(sub.tenantSequence) FROM AuditLog sub WHERE sub.tenantId = :tenantId)")
    Optional<AuditLog> findLatestRecordByTenantId(@Param("tenantId") UUID tenantId);

    Page<AuditLog> findByTenantId(UUID tenantId, Pageable pageable);

    Page<AuditLog> findByTraceId(String traceId, Pageable pageable);
}

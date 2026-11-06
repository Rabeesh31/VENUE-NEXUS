package com.venuenexus.audit.port;

import com.venuenexus.audit.model.AuditRecord;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Storage port for audit ledger records.
 * Implemented in Phase 1 as an in-memory store; implemented in Phase 2 backed by Flyway V4 audit_log.
 */
public interface AuditLogStore {

    Optional<AuditRecord> findLatestByTenant(UUID tenantId);

    void save(AuditRecord record);

    List<AuditRecord> findAllByTenantOrderBySequenceAsc(UUID tenantId);

    int countByTenant(UUID tenantId);

    void clear();
}

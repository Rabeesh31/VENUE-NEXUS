package com.venuenexus.audit.port;

import com.venuenexus.audit.model.AuditRecord;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Storage port for audit ledger records and tenant chain sequencing.
 * Implemented in Phase 1 as InMemoryAuditLogStore (for unit tests) and
 * JdbcAuditLogStore (backed by audit_chain and audit_log PostgreSQL tables).
 * Replaced in Phase 2 by Flyway V4 persistence.
 */
public interface AuditLogStore {

    /**
     * Snapshot of the current state of a tenant/platform audit chain counter.
     */
    record ChainState(long lastSequence, String lastHash) {
    }

    /**
     * Acquires an exclusive row lock on the tenant's chain row and returns its current sequence and hash.
     * In JDBC, executes INSERT ... ON CONFLICT DO NOTHING followed by SELECT ... FOR UPDATE.
     *
     * @param tenantId Tenant ID (or null for platform chain)
     * @return Current ChainState (lastSequence, lastHash)
     */
    ChainState lockChain(UUID tenantId);

    /**
     * Releases any in-memory lock if held by current thread (no-op for JDBC where transaction boundary manages locks).
     *
     * @param tenantId Tenant ID (or null for platform chain)
     */
    default void releaseLock(UUID tenantId) {
    }

    /**
     * Persists an audit record and advances the chain counter.
     *
     * @param record Audit record to persist
     */
    void save(AuditRecord record);

    /**
     * Finds the latest record for a given tenant (or platform if tenantId is null).
     */
    Optional<AuditRecord> findLatestByTenant(UUID tenantId);

    /**
     * Returns all records for a tenant (or platform if tenantId is null) ordered by sequence ascending.
     */
    List<AuditRecord> findAllByTenantOrderBySequenceAsc(UUID tenantId);

    /**
     * Returns the total count of audit records for a tenant (or platform if tenantId is null).
     */
    int countByTenant(UUID tenantId);

    /**
     * Clears all stored records and chain rows (test support).
     */
    void clear();
}

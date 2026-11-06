package com.venuenexus.audit.adapter;

import com.venuenexus.audit.model.AuditRecord;
import com.venuenexus.audit.port.AuditLogStore;
import org.springframework.stereotype.Repository;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * In-memory adapter for AuditLogStore port.
 * Allows Phase 1 audit chain verification and tests without requiring Flyway V4 schema.
 */
@Repository
public class InMemoryAuditLogStore implements AuditLogStore {

    // Sentinel key representing platform-level events (where tenantId is null)
    private static final UUID PLATFORM_TENANT_SENTINEL = new UUID(0L, 0L);

    private final Map<UUID, List<AuditRecord>> store = new ConcurrentHashMap<>();

    @Override
    public Optional<AuditRecord> findLatestByTenant(UUID tenantId) {
        List<AuditRecord> chain = getChain(tenantId);
        synchronized (chain) {
            if (chain.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(chain.get(chain.size() - 1));
        }
    }

    @Override
    public void save(AuditRecord record) {
        List<AuditRecord> chain = getChain(record.tenantId());
        synchronized (chain) {
            chain.add(record);
        }
    }

    @Override
    public List<AuditRecord> findAllByTenantOrderBySequenceAsc(UUID tenantId) {
        List<AuditRecord> chain = getChain(tenantId);
        synchronized (chain) {
            return List.copyOf(chain);
        }
    }

    @Override
    public int countByTenant(UUID tenantId) {
        List<AuditRecord> chain = getChain(tenantId);
        synchronized (chain) {
            return chain.size();
        }
    }

    @Override
    public void clear() {
        store.clear();
    }

    private List<AuditRecord> getChain(UUID tenantId) {
        UUID key = (tenantId != null) ? tenantId : PLATFORM_TENANT_SENTINEL;
        return store.computeIfAbsent(key, k -> new CopyOnWriteArrayList<>());
    }
}

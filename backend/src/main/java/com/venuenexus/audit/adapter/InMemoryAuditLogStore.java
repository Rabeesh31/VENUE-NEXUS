package com.venuenexus.audit.adapter;

import com.venuenexus.audit.model.AuditRecord;
import com.venuenexus.audit.port.AuditLogStore;
import com.venuenexus.audit.service.AuditLogService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Repository;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.locks.ReentrantLock;

/**
 * In-memory adapter for AuditLogStore port.
 * Maintained for fast, zero-dependency unit tests without requiring a running database.
 */
public class InMemoryAuditLogStore implements AuditLogStore {

    // Sentinel key representing platform-level events (where tenantId is null)
    private static final UUID PLATFORM_TENANT_SENTINEL = new UUID(0L, 0L);

    private final Map<UUID, List<AuditRecord>> store = new ConcurrentHashMap<>();
    private final Map<UUID, ReentrantLock> chainLocks = new ConcurrentHashMap<>();

    @Override
    public ChainState lockChain(UUID tenantId) {
        UUID key = (tenantId != null) ? tenantId : PLATFORM_TENANT_SENTINEL;
        ReentrantLock lock = chainLocks.computeIfAbsent(key, k -> new ReentrantLock());
        lock.lock();

        List<AuditRecord> chain = getChain(tenantId);
        synchronized (chain) {
            if (chain.isEmpty()) {
                return new ChainState(0L, AuditLogService.GENESIS_PREV_HASH);
            }
            AuditRecord latest = chain.get(chain.size() - 1);
            return new ChainState(latest.sequence(), latest.hash());
        }
    }

    @Override
    public void releaseLock(UUID tenantId) {
        UUID key = (tenantId != null) ? tenantId : PLATFORM_TENANT_SENTINEL;
        ReentrantLock lock = chainLocks.get(key);
        if (lock != null && lock.isHeldByCurrentThread()) {
            lock.unlock();
        }
    }

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
        try {
            List<AuditRecord> chain = getChain(record.tenantId());
            synchronized (chain) {
                chain.add(record);
            }
        } finally {
            releaseLock(record.tenantId());
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

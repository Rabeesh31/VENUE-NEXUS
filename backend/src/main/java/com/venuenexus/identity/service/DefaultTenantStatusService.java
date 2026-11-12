package com.venuenexus.identity.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
@ConditionalOnMissingBean(name = "realTenantStatusService")
public class DefaultTenantStatusService implements TenantStatusService {

    private final Set<UUID> suspendedTenants = Collections.newSetFromMap(new ConcurrentHashMap<>());

    @Override
    public boolean isTenantSuspended(UUID tenantId) {
        if (tenantId == null) {
            return false;
        }
        return suspendedTenants.contains(tenantId);
    }

    public void suspendTenant(UUID tenantId) {
        if (tenantId != null) {
            suspendedTenants.add(tenantId);
        }
    }

    public void reactivateTenant(UUID tenantId) {
        if (tenantId != null) {
            suspendedTenants.remove(tenantId);
        }
    }

    public void clear() {
        suspendedTenants.clear();
    }
}


package com.venuenexus.security.authz;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * Initial stub implementation of {@link ScopeEnforcer}.
 * Provides default behavior during early phase integration.
 * Full scope validation rules are activated in P3-10.
 */
@Component
@ConditionalOnMissingBean(name = "realScopeEnforcer")
public class DefaultScopeEnforcer implements ScopeEnforcer {

    @Override
    public void assertCanAccess(UUID recordOrganisationId, UUID recordLocationId) {
        // Stub implementation: allows access during initial bootstrap until P3-10
    }

    @Override
    public boolean canAccess(UUID recordOrganisationId, UUID recordLocationId) {
        return true;
    }

    @Override
    public Optional<UUID> restrictedLocationId() {
        return Optional.empty();
    }

    @Override
    public Optional<UUID> restrictedOrganisationId() {
        return Optional.empty();
    }
}


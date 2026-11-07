package com.venuenexus.security.authz;

import com.venuenexus.audit.model.AuditEvent;
import com.venuenexus.audit.model.AuditEventType;
import com.venuenexus.audit.service.AuditLogService;
import com.venuenexus.common.error.ApiException;
import com.venuenexus.common.error.ErrorCode;
import com.venuenexus.common.filter.TraceIdFilter;
import com.venuenexus.security.context.CurrentStaff;
import com.venuenexus.security.context.ScopeContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Production implementation of {@link ScopeEnforcer} enforcing hierarchical multi-tenant
 * boundaries: Tenant &gt; Organisation &gt; Location.
 *
 * <p><strong>CRITICAL SECURITY INVARIANTS:</strong>
 * <ul>
 *   <li>Every tenant-owned entity query MUST first be tenant-scoped in the repository.</li>
 *   <li>Location-scoped staff cannot access records of another location in the same tenant.</li>
 *   <li>Organisation-scoped staff cannot access records of another organisation in the same tenant.</li>
 *   <li>Platform administrators cannot directly access raw tenant business data.</li>
 *   <li>Out-of-scope access attempts MUST yield {@link ErrorCode#RESOURCE_NOT_FOUND} (404) to prevent resource enumeration.</li>
 * </ul>
 */
@Service("realScopeEnforcer")
@Primary
public class ScopeEnforcerImpl implements ScopeEnforcer {

    private static final Logger log = LoggerFactory.getLogger(ScopeEnforcerImpl.class);

    private final AuditLogService auditLogService;

    public ScopeEnforcerImpl(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    @Override
    public void assertCanAccess(UUID recordOrganisationId, UUID recordLocationId) {
        if (!canAccess(recordOrganisationId, recordLocationId)) {
            ScopeContext scope = ScopeContext.current().orElse(null);
            UUID tenantId = scope != null ? scope.tenantId() : null;
            UUID userId = CurrentStaff.current().map(CurrentStaff::userId).orElse(null);

            log.warn("Scope access denied [userId={}, tenantId={}, recordOrgId={}, recordLocId={}]",
                    userId, tenantId, recordOrganisationId, recordLocationId);

            if (auditLogService != null) {
                auditLogService.record(AuditEvent.failure(
                        tenantId,
                        userId,
                        AuditEventType.AUTH_LOGIN_FAILED,
                        TraceIdFilter.getCurrentTraceId(),
                        "127.0.0.1",
                        Map.of(
                                "action", "ACCESS_DENIED_SCOPE",
                                "recordOrgId", String.valueOf(recordOrganisationId),
                                "recordLocId", String.valueOf(recordLocationId)
                        )
                ));
            }

            // Invariant: out-of-scope records appear non-existent (404)
            throw new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Resource not found");
        }
    }

    @Override
    public boolean canAccess(UUID recordOrganisationId, UUID recordLocationId) {
        ScopeContext scope = ScopeContext.current().orElse(null);
        if (scope == null) {
            return false;
        }

        // Platform Admin cannot access raw tenant operational data
        if (scope.isPlatformScoped()) {
            return false;
        }

        // Location-scoped staff: record MUST match user's assigned location
        if (scope.isLocationScoped()) {
            return recordLocationId != null && recordLocationId.equals(scope.locationId());
        }

        // Organisation-scoped staff: record MUST match user's assigned organisation
        if (scope.isOrganisationScoped()) {
            return recordOrganisationId != null && recordOrganisationId.equals(scope.organisationId());
        }

        // Tenant-level staff: unrestricted within own tenant
        return scope.isTenantLevel();
    }

    @Override
    public Optional<UUID> restrictedLocationId() {
        return ScopeContext.current()
                .filter(ScopeContext::isLocationScoped)
                .map(ScopeContext::locationId);
    }

    @Override
    public Optional<UUID> restrictedOrganisationId() {
        return ScopeContext.current()
                .filter(ScopeContext::isOrganisationScoped)
                .map(ScopeContext::organisationId);
    }
}

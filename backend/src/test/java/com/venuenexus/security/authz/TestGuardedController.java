package com.venuenexus.security.authz;

import com.venuenexus.common.error.ApiException;
import com.venuenexus.common.error.ErrorCode;
import com.venuenexus.security.context.TenantContext;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Test controller demonstrating RBAC, multi-tenancy, and hierarchical scope enforcement
 * for Phase 4 and Phase 5 service implementations.
 */
@RestController
public class TestGuardedController {

    private final ScopeEnforcer scopeEnforcer;
    private final Map<UUID, TestRecord> recordStore = new ConcurrentHashMap<>();

    public TestGuardedController(ScopeEnforcer scopeEnforcer) {
        this.scopeEnforcer = scopeEnforcer;
    }

    public void addRecord(TestRecord record) {
        recordStore.put(record.id(), record);
    }

    public void clearStore() {
        recordStore.clear();
    }

    @GetMapping({"/test/platform-only", "/api/v1/test/platform-only"})
    @PreAuthorize(SecurityExpressions.PLATFORM_ONLY)
    public ResponseEntity<Map<String, String>> platformOnly() {
        return ResponseEntity.ok(Map.of("message", "platform access granted"));
    }

    @GetMapping({"/test/tenant-admin-only", "/api/v1/test/tenant-admin-only"})
    @PreAuthorize(SecurityExpressions.TENANT_ADMIN_ONLY)
    public ResponseEntity<Map<String, String>> tenantAdminOnly() {
        return ResponseEntity.ok(Map.of("message", "tenant admin access granted"));
    }

    @GetMapping({"/test/manager-and-above", "/api/v1/test/manager-and-above"})
    @PreAuthorize(SecurityExpressions.MANAGER_AND_ABOVE)
    public ResponseEntity<Map<String, String>> managerAndAbove() {
        return ResponseEntity.ok(Map.of("message", "manager access granted"));
    }

    @GetMapping({"/test/supervisor-and-above", "/api/v1/test/supervisor-and-above"})
    @PreAuthorize(SecurityExpressions.SUPERVISOR_AND_ABOVE)
    public ResponseEntity<Map<String, String>> supervisorAndAbove() {
        return ResponseEntity.ok(Map.of("message", "supervisor access granted"));
    }

    @GetMapping({"/test/any-staff", "/api/v1/test/any-staff"})
    @PreAuthorize(SecurityExpressions.ANY_STAFF_ROLE)
    public ResponseEntity<Map<String, String>> anyStaff() {
        return ResponseEntity.ok(Map.of("message", "staff access granted"));
    }

    @GetMapping({"/test/records/{id}", "/api/v1/test/records/{id}"})
    public ResponseEntity<TestRecord> getRecordById(@PathVariable UUID id) {
        TestRecord record = recordStore.get(id);
        if (record == null) {
            throw new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Resource not found");
        }

        // 1. Cross-tenant check: out-of-tenant records yield 404
        TenantScopedGuard.assertSameTenant(record.tenantId());

        // 2. Hierarchical scope check: org / location / platform scope violations yield 404
        scopeEnforcer.assertCanAccess(record.organisationId(), record.locationId());

        return ResponseEntity.ok(record);
    }

    @GetMapping({"/test/records", "/api/v1/test/records"})
    public ResponseEntity<List<TestRecord>> listRecords(
            @RequestParam(name = "tenantId", required = false) UUID untrustedTenantId) {
        // Invariant: Never trust client-supplied tenantId parameter; strictly use TenantContext
        UUID callerTenantId = TenantContext.requireTenantId();

        Optional<UUID> restrictedOrgId = scopeEnforcer.restrictedOrganisationId();
        Optional<UUID> restrictedLocId = scopeEnforcer.restrictedLocationId();

        List<TestRecord> results = new ArrayList<>();
        for (TestRecord r : recordStore.values()) {
            if (!r.tenantId().equals(callerTenantId)) {
                continue;
            }
            if (restrictedOrgId.isPresent() && !restrictedOrgId.get().equals(r.organisationId())) {
                continue;
            }
            if (restrictedLocId.isPresent() && !restrictedLocId.get().equals(r.locationId())) {
                continue;
            }
            results.add(r);
        }

        return ResponseEntity.ok(results);
    }
}


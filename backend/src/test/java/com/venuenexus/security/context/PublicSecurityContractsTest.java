package com.venuenexus.security.context;

import com.venuenexus.common.error.ApiException;
import com.venuenexus.common.error.ErrorCode;
import com.venuenexus.security.StaffRole;
import com.venuenexus.security.SecurityConfig;
import com.venuenexus.security.authz.DefaultScopeEnforcer;
import com.venuenexus.security.authz.Roles;
import com.venuenexus.security.authz.ScopeEnforcer;
import com.venuenexus.security.authz.SecurityExpressions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PublicSecurityContractsTest {

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        ScopeContext.clear();
        CurrentStaff.clear();
    }

    @Test
    void testTenantContextOperations() {
        assertThat(TenantContext.currentTenantId()).isEmpty();
        assertThatThrownBy(TenantContext::requireTenantId)
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Tenant context required")
                .satisfies(ex -> assertThat(((ApiException) ex).getErrorCode()).isEqualTo(ErrorCode.ACCESS_DENIED));

        UUID tenantId = UUID.randomUUID();
        TenantContext.setTenantId(tenantId);

        assertThat(TenantContext.currentTenantId()).contains(tenantId);
        assertThat(TenantContext.requireTenantId()).isEqualTo(tenantId);

        TenantContext.clear();
        assertThat(TenantContext.currentTenantId()).isEmpty();
    }

    @Test
    void testScopeContextOperations() {
        assertThat(ScopeContext.current()).isEmpty();
        assertThatThrownBy(ScopeContext::require)
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getErrorCode()).isEqualTo(ErrorCode.ACCESS_DENIED));

        UUID tenantId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        UUID locId = UUID.randomUUID();
        UUID depId = UUID.randomUUID();

        // Platform Admin
        ScopeContext platformScope = new ScopeContext(StaffRole.PLATFORM_ADMIN, null, null, null, null);
        assertThat(platformScope.isPlatformScoped()).isTrue();
        assertThat(platformScope.isTenantLevel()).isFalse();

        // Tenant Level
        ScopeContext tenantScope = new ScopeContext(StaffRole.TENANT_ADMIN, tenantId, null, null, null);
        assertThat(tenantScope.isPlatformScoped()).isFalse();
        assertThat(tenantScope.isTenantLevel()).isTrue();
        assertThat(tenantScope.isOrganisationScoped()).isFalse();
        assertThat(tenantScope.isLocationScoped()).isFalse();

        // Org Scoped
        ScopeContext orgScope = new ScopeContext(StaffRole.VENUE_MANAGER, tenantId, orgId, null, null);
        assertThat(orgScope.isOrganisationScoped()).isTrue();
        assertThat(orgScope.isLocationScoped()).isFalse();

        // Location Scoped
        ScopeContext locScope = new ScopeContext(StaffRole.STAFF, tenantId, orgId, locId, depId);
        assertThat(locScope.isLocationScoped()).isTrue();

        ScopeContext.set(locScope);
        assertThat(ScopeContext.current()).contains(locScope);
        assertThat(ScopeContext.require()).isEqualTo(locScope);

        ScopeContext.clear();
        assertThat(ScopeContext.current()).isEmpty();
    }

    @Test
    void testCurrentStaffOperations() {
        assertThat(CurrentStaff.current()).isEmpty();
        assertThatThrownBy(CurrentStaff::require)
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getErrorCode()).isEqualTo(ErrorCode.ACCESS_DENIED));

        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        String tokenId = UUID.randomUUID().toString();

        CurrentStaff staff = new CurrentStaff(userId, StaffRole.VENUE_MANAGER, tenantId, null, null, null, tokenId);
        CurrentStaff.set(staff);

        assertThat(CurrentStaff.current()).contains(staff);
        assertThat(CurrentStaff.require()).isEqualTo(staff);

        CurrentStaff.clear();
        assertThat(CurrentStaff.current()).isEmpty();
    }

    @Test
    void testRolesAndSecurityExpressionsConstants() {
        assertThat(Roles.PLATFORM_ADMIN).isEqualTo("PLATFORM_ADMIN");
        assertThat(Roles.ROLE_PLATFORM_ADMIN).isEqualTo("ROLE_PLATFORM_ADMIN");
        assertThat(Roles.ROLE_STAFF).isEqualTo("ROLE_STAFF");

        assertThat(SecurityExpressions.PLATFORM_ONLY).isEqualTo("hasRole('PLATFORM_ADMIN')");
        assertThat(SecurityExpressions.TENANT_ADMIN_ONLY).isEqualTo("hasRole('TENANT_ADMIN')");
        assertThat(SecurityExpressions.MANAGER_AND_ABOVE).isEqualTo("hasAnyRole('TENANT_ADMIN','VENUE_MANAGER')");
        assertThat(SecurityExpressions.SUPERVISOR_AND_ABOVE).isEqualTo("hasAnyRole('TENANT_ADMIN','VENUE_MANAGER','SUPERVISOR')");
        assertThat(SecurityExpressions.ANY_STAFF_ROLE).isEqualTo("hasAnyRole('TENANT_ADMIN','VENUE_MANAGER','SUPERVISOR','STAFF')");
    }

    @Test
    void testDefaultScopeEnforcerStub() {
        ScopeEnforcer enforcer = new DefaultScopeEnforcer();
        UUID orgId = UUID.randomUUID();
        UUID locId = UUID.randomUUID();

        // Stub allows access without throwing
        enforcer.assertCanAccess(orgId, locId);
        assertThat(enforcer.canAccess(orgId, locId)).isTrue();
        assertThat(enforcer.restrictedLocationId()).isEmpty();
        assertThat(enforcer.restrictedOrganisationId()).isEmpty();
    }

    @Test
    void testPasswordEncoderBean() {
        SecurityConfig config = new SecurityConfig();
        PasswordEncoder encoder = config.passwordEncoder();
        assertThat(encoder).isNotNull();

        String raw = "StrongP@ssw0rd123";
        String encoded = encoder.encode(raw);
        assertThat(encoded).startsWith("$2a$").isNotEqualTo(raw);
        assertThat(encoder.matches(raw, encoded)).isTrue();
        assertThat(encoder.matches("wrong", encoded)).isFalse();
    }
}


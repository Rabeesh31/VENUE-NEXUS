package com.venuenexus.security.authz;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.venuenexus.audit.model.AuditEvent;
import com.venuenexus.audit.model.AuditEventType;
import com.venuenexus.audit.service.AuditLogService;
import com.venuenexus.common.filter.TraceIdFilter;
import com.venuenexus.security.StaffRole;
import com.venuenexus.security.denylist.TokenDenylist;
import com.venuenexus.security.jwt.StaffJwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import com.venuenexus.VenueNexusApplication;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = VenueNexusApplication.class)
@AutoConfigureMockMvc
class ScopeEnforcerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StaffJwtService staffJwtService;

    @Autowired
    private TestGuardedController testController;

    @MockBean
    private AuditLogService auditLogService;

    @MockBean
    private TokenDenylist tokenDenylist;

    private static final UUID TENANT_A = UUID.randomUUID();
    private static final UUID TENANT_B = UUID.randomUUID();

    private static final UUID ORG_1 = UUID.randomUUID();
    private static final UUID ORG_2 = UUID.randomUUID();
    private static final UUID ORG_B = UUID.randomUUID();

    private static final UUID LOC_1 = UUID.randomUUID();
    private static final UUID LOC_2 = UUID.randomUUID();
    private static final UUID LOC_3 = UUID.randomUUID();
    private static final UUID LOC_B = UUID.randomUUID();

    private TestRecord recordA1;
    private TestRecord recordA2;
    private TestRecord recordA3;
    private TestRecord recordB;

    @BeforeEach
    void setUp() {
        testController.clearStore();
        when(tokenDenylist.isDenylisted(anyString())).thenReturn(false);

        // Seed tenant A records across different orgs and locations
        recordA1 = new TestRecord(UUID.randomUUID(), TENANT_A, ORG_1, LOC_1, "Tenant A - Org 1 - Loc 1");
        recordA2 = new TestRecord(UUID.randomUUID(), TENANT_A, ORG_1, LOC_2, "Tenant A - Org 1 - Loc 2");
        recordA3 = new TestRecord(UUID.randomUUID(), TENANT_A, ORG_2, LOC_3, "Tenant A - Org 2 - Loc 3");

        // Seed tenant B record
        recordB = new TestRecord(UUID.randomUUID(), TENANT_B, ORG_B, LOC_B, "Tenant B - Org B - Loc B");

        testController.addRecord(recordA1);
        testController.addRecord(recordA2);
        testController.addRecord(recordA3);
        testController.addRecord(recordB);
    }

    private String createBearerToken(StaffRole role, UUID tenantId, UUID orgId, UUID locId) {
        return "Bearer " + staffJwtService.issueToken(
                UUID.randomUUID(),
                role,
                tenantId,
                orgId,
                locId,
                null
        ).token();
    }

    @Nested
    @DisplayName("T-49, T-50, T-51, T-52, T-53: RBAC Method Security")
    class RbacMethodSecurityTests {

        @Test
        void testPlatformAdminOnly_T49() throws Exception {
            String platformToken = createBearerToken(StaffRole.PLATFORM_ADMIN, null, null, null);
            String tenantAdminToken = createBearerToken(StaffRole.TENANT_ADMIN, TENANT_A, null, null);

            mockMvc.perform(get("/api/v1/test/platform-only").header("Authorization", platformToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").value("platform access granted"));

            mockMvc.perform(get("/api/v1/test/platform-only").header("Authorization", tenantAdminToken))
                    .andExpect(status().isForbidden());
        }

        @Test
        void testTenantAdminOnly_T50() throws Exception {
            String tenantAdminToken = createBearerToken(StaffRole.TENANT_ADMIN, TENANT_A, null, null);
            String venueManagerToken = createBearerToken(StaffRole.VENUE_MANAGER, TENANT_A, ORG_1, LOC_1);

            mockMvc.perform(get("/api/v1/test/tenant-admin-only").header("Authorization", tenantAdminToken))
                    .andExpect(status().isOk());

            mockMvc.perform(get("/api/v1/test/tenant-admin-only").header("Authorization", venueManagerToken))
                    .andExpect(status().isForbidden());
        }

        @Test
        void testManagerAndAbove_T51() throws Exception {
            String tenantAdminToken = createBearerToken(StaffRole.TENANT_ADMIN, TENANT_A, null, null);
            String venueManagerToken = createBearerToken(StaffRole.VENUE_MANAGER, TENANT_A, ORG_1, LOC_1);
            String supervisorToken = createBearerToken(StaffRole.SUPERVISOR, TENANT_A, ORG_1, LOC_1);

            mockMvc.perform(get("/api/v1/test/manager-and-above").header("Authorization", tenantAdminToken))
                    .andExpect(status().isOk());
            mockMvc.perform(get("/api/v1/test/manager-and-above").header("Authorization", venueManagerToken))
                    .andExpect(status().isOk());

            mockMvc.perform(get("/api/v1/test/manager-and-above").header("Authorization", supervisorToken))
                    .andExpect(status().isForbidden());
        }

        @Test
        void testSupervisorAndAbove_T52() throws Exception {
            String venueManagerToken = createBearerToken(StaffRole.VENUE_MANAGER, TENANT_A, ORG_1, LOC_1);
            String supervisorToken = createBearerToken(StaffRole.SUPERVISOR, TENANT_A, ORG_1, LOC_1);
            String staffToken = createBearerToken(StaffRole.STAFF, TENANT_A, ORG_1, LOC_1);

            mockMvc.perform(get("/api/v1/test/supervisor-and-above").header("Authorization", venueManagerToken))
                    .andExpect(status().isOk());
            mockMvc.perform(get("/api/v1/test/supervisor-and-above").header("Authorization", supervisorToken))
                    .andExpect(status().isOk());

            mockMvc.perform(get("/api/v1/test/supervisor-and-above").header("Authorization", staffToken))
                    .andExpect(status().isForbidden());
        }

        @Test
        void testAnyStaffRole_T53() throws Exception {
            String tenantAdminToken = createBearerToken(StaffRole.TENANT_ADMIN, TENANT_A, null, null);
            String venueManagerToken = createBearerToken(StaffRole.VENUE_MANAGER, TENANT_A, ORG_1, LOC_1);
            String supervisorToken = createBearerToken(StaffRole.SUPERVISOR, TENANT_A, ORG_1, LOC_1);
            String staffToken = createBearerToken(StaffRole.STAFF, TENANT_A, ORG_1, LOC_1);
            String platformToken = createBearerToken(StaffRole.PLATFORM_ADMIN, null, null, null);

            mockMvc.perform(get("/api/v1/test/any-staff").header("Authorization", tenantAdminToken))
                    .andExpect(status().isOk());
            mockMvc.perform(get("/api/v1/test/any-staff").header("Authorization", venueManagerToken))
                    .andExpect(status().isOk());
            mockMvc.perform(get("/api/v1/test/any-staff").header("Authorization", supervisorToken))
                    .andExpect(status().isOk());
            mockMvc.perform(get("/api/v1/test/any-staff").header("Authorization", staffToken))
                    .andExpect(status().isOk());

            mockMvc.perform(get("/api/v1/test/any-staff").header("Authorization", platformToken))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("T-54, T-55, T-56: Multi-Tenant Isolation")
    class MultiTenantTests {

        @Test
        void testCrossTenantLookup_Returns404_T54() throws Exception {
            // Tenant A token attempts to look up record B from Tenant B -> 404 (not found)
            String tenantAToken = createBearerToken(StaffRole.TENANT_ADMIN, TENANT_A, null, null);

            mockMvc.perform(get("/api/v1/test/records/" + recordB.id()).header("Authorization", tenantAToken))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"))
                    .andExpect(jsonPath("$.message").value("Resource not found"));
        }

        @Test
        void testUntrustedTenantIdParam_Ignored_T55() throws Exception {
            // Caller has Tenant A token, but sends tenantId=TenantB in query parameter
            String tenantAToken = createBearerToken(StaffRole.TENANT_ADMIN, TENANT_A, null, null);

            mockMvc.perform(get("/api/v1/test/records")
                            .param("tenantId", TENANT_B.toString())
                            .header("Authorization", tenantAToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(3)))
                    .andExpect(jsonPath("$[0].tenantId").value(TENANT_A.toString()))
                    .andExpect(jsonPath("$[1].tenantId").value(TENANT_A.toString()))
                    .andExpect(jsonPath("$[2].tenantId").value(TENANT_A.toString()));
        }

        @Test
        void testListRecords_NeverContainsOtherTenantRows_T56() throws Exception {
            String tenantBToken = createBearerToken(StaffRole.TENANT_ADMIN, TENANT_B, null, null);

            mockMvc.perform(get("/api/v1/test/records").header("Authorization", tenantBToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(1)))
                    .andExpect(jsonPath("$[0].id").value(recordB.id().toString()))
                    .andExpect(jsonPath("$[0].tenantId").value(TENANT_B.toString()));
        }
    }

    @Nested
    @DisplayName("T-57, T-58, T-59, T-60: Hierarchical Scope Enforcement")
    class ScopeEnforcementTests {

        @Test
        void testLocationScopedUser_SameLocationAllowed_OtherLocationReturns404_T57() throws Exception {
            // User assigned to LOC_1
            String loc1Token = createBearerToken(StaffRole.STAFF, TENANT_A, ORG_1, LOC_1);

            // Access record in LOC_1 -> 200 OK
            mockMvc.perform(get("/api/v1/test/records/" + recordA1.id()).header("Authorization", loc1Token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(recordA1.id().toString()));

            // Access record in LOC_2 (same tenant, same org, but different location) -> 404 RESOURCE_NOT_FOUND
            mockMvc.perform(get("/api/v1/test/records/" + recordA2.id()).header("Authorization", loc1Token))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));

            // Verify Audit Log records event
            ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
            verify(auditLogService).record(captor.capture());
            assertThat(captor.getValue().eventType()).isEqualTo(AuditEventType.AUTH_LOGIN_FAILED);
            assertThat(captor.getValue().details().get("action")).isEqualTo("ACCESS_DENIED_SCOPE");
            assertThat(captor.getValue().tenantId()).isEqualTo(TENANT_A);
        }

        @Test
        void testOrganisationScopedUser_SameOrgAllowed_OtherOrgReturns404_T58() throws Exception {
            // User assigned to ORG_1 (no location constraint)
            String org1Token = createBearerToken(StaffRole.VENUE_MANAGER, TENANT_A, ORG_1, null);

            // Access record in ORG_1, LOC_1 -> 200 OK
            mockMvc.perform(get("/api/v1/test/records/" + recordA1.id()).header("Authorization", org1Token))
                    .andExpect(status().isOk());

            // Access record in ORG_1, LOC_2 -> 200 OK
            mockMvc.perform(get("/api/v1/test/records/" + recordA2.id()).header("Authorization", org1Token))
                    .andExpect(status().isOk());

            // Access record in ORG_2, LOC_3 -> 404 RESOURCE_NOT_FOUND
            mockMvc.perform(get("/api/v1/test/records/" + recordA3.id()).header("Authorization", org1Token))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
        }

        @Test
        void testTenantLevelUser_CanAccessAnyLocationInTenant_T59() throws Exception {
            // Tenant Admin has no org or location restrictions
            String tenantLevelToken = createBearerToken(StaffRole.TENANT_ADMIN, TENANT_A, null, null);

            mockMvc.perform(get("/api/v1/test/records/" + recordA1.id()).header("Authorization", tenantLevelToken))
                    .andExpect(status().isOk());
            mockMvc.perform(get("/api/v1/test/records/" + recordA2.id()).header("Authorization", tenantLevelToken))
                    .andExpect(status().isOk());
            mockMvc.perform(get("/api/v1/test/records/" + recordA3.id()).header("Authorization", tenantLevelToken))
                    .andExpect(status().isOk());
        }

        @Test
        void testPlatformAdmin_CannotAccessTenantBusinessData_T60() throws Exception {
            String platformToken = createBearerToken(StaffRole.PLATFORM_ADMIN, null, null, null);

            // Attempting to access raw tenant business data returns 404
            mockMvc.perform(get("/api/v1/test/records/" + recordA1.id()).header("Authorization", platformToken))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
        }
    }
}

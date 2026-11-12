package com.venuenexus.resource.it;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.venuenexus.VenueNexusApplication;
import com.venuenexus.resource.domain.*;
import com.venuenexus.resource.dto.Dtos.*;
import com.venuenexus.resource.port.Ports.AuditPort;
import com.venuenexus.resource.port.Ports.CallerContext;
import com.venuenexus.resource.port.Ports.VenueLookup;
import com.venuenexus.resource.repo.ResourceQrRepository;
import com.venuenexus.resource.repo.ResourceRepository;
import com.venuenexus.resource.service.QrService;
import com.venuenexus.resource.service.ResourceService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PostgreSQL Testcontainers Integration Test.
 *
 * Executes tests against the official Phase 2 V3 Flyway migration schema
 * (V3__resource_and_qr.sql) including foreign keys, JSONB capabilities,
 * composite uniqueness, and the partial unique index on active QR tokens.
 *
 * NOTE: Skipped gracefully when Docker daemon is not available on the execution host.
 */
@ExtendWith(ResourcePostgresIntegrationTest.DockerCondition.class)
@SpringBootTest(classes = {VenueNexusApplication.class, ResourcePostgresIntegrationTest.TestPortsConfig.class})
class ResourcePostgresIntegrationTest {

    public static class DockerCondition implements ExecutionCondition {
        @Override
        public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
            try {
                if (DockerClientFactory.instance().isDockerAvailable()) {
                    return ConditionEvaluationResult.enabled("Docker daemon is available for Testcontainers.");
                }
            } catch (Throwable ignored) {
            }
            return ConditionEvaluationResult.disabled("Docker daemon is not running on this host; skipping PostgreSQL container tests.");
        }
    }

    private static PostgreSQLContainer<?> postgres;

    static {
        try {
            if (DockerClientFactory.instance().isDockerAvailable()) {
                postgres = new PostgreSQLContainer<>("postgres:16-alpine")
                        .withDatabaseName("venuenexus_test")
                        .withUsername("test_user")
                        .withPassword("test_password");
                postgres.start();
            }
        } catch (Throwable ignored) {
            // Handled by DockerCondition
        }
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        if (postgres != null && postgres.isRunning()) {
            registry.add("spring.datasource.url", postgres::getJdbcUrl);
            registry.add("spring.datasource.username", postgres::getUsername);
            registry.add("spring.datasource.password", postgres::getPassword);
            registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
            registry.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.PostgreSQLDialect");
            registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
        }
    }

    private static final UUID TENANT_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID USER_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");

    @TestConfiguration
    static class TestPortsConfig {
        @Bean
        public CallerContext callerContext() {
            return new CallerContext() {
                @Override public UUID tenantId() { return TENANT_ID; }
                @Override public UUID userId() { return USER_ID; }
                @Override public Set<UUID> allowedLocationIds() { return null; }
            };
        }

        @Bean
        public VenueLookup venueLookup() {
            return new VenueLookup() {
                @Override public boolean locationExists(UUID tenantId, UUID locationId) { return true; }
                @Override public boolean spaceBelongsToLocation(UUID tenantId, UUID spaceId, UUID locationId) { return true; }
                @Override public boolean departmentBelongsToLocation(UUID tenantId, UUID departmentId, UUID locationId) { return true; }
            };
        }

        @Bean
        public AuditPort auditPort() {
            return (eventType, tenantId, actorUserId, detail) -> {};
        }
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ResourceRepository resourceRepository;

    @Autowired
    private ResourceQrRepository resourceQrRepository;

    @Autowired
    private ResourceService resourceService;

    @Autowired
    private QrService qrService;

    @BeforeEach
    void setupV3Schema() {
        Assumptions.assumeTrue(postgres != null && postgres.isRunning(), "PostgreSQL container must be running");

        // Parent hierarchy tables required by Phase 2 V3 foreign key constraints
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS tenant (
                id UUID PRIMARY KEY,
                name VARCHAR(255) NOT NULL
            );
            """);

        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS location (
                id UUID PRIMARY KEY,
                tenant_id UUID NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
                name VARCHAR(255) NOT NULL
            );
            """);

        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS app_user (
                id UUID PRIMARY KEY,
                tenant_id UUID REFERENCES tenant(id) ON DELETE CASCADE,
                email VARCHAR(255) NOT NULL
            );
            """);

        // Official Phase 2 Flyway V3 Schema (V3__resource_and_qr.sql)
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS resource (
                id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                tenant_id UUID NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
                location_id UUID NOT NULL REFERENCES location(id) ON DELETE CASCADE,
                space_id UUID,
                department_id UUID,
                resource_code VARCHAR(100) NOT NULL,
                display_name VARCHAR(255) NOT NULL,
                resource_type VARCHAR(50) NOT NULL,
                status VARCHAR(50) NOT NULL DEFAULT 'AVAILABLE' CHECK (status IN ('AVAILABLE', 'OCCUPIED', 'RESERVED', 'MAINTENANCE', 'DISABLED')),
                activation_policy VARCHAR(50) NOT NULL DEFAULT 'INSTANT' CHECK (activation_policy IN ('INSTANT', 'STAFF_CONFIRM', 'OTP_ONLY')),
                capabilities JSONB,
                created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                CONSTRAINT uq_resource_tenant_location_code UNIQUE (tenant_id, location_id, resource_code)
            );
            """);

        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS resource_qr (
                id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                resource_id UUID NOT NULL REFERENCES resource(id) ON DELETE CASCADE,
                token_hash VARCHAR(64) NOT NULL,
                version INT NOT NULL DEFAULT 1,
                status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'REVOKED')),
                issued_by_user_id UUID REFERENCES app_user(id) ON DELETE SET NULL,
                revoked_at TIMESTAMPTZ,
                revocation_reason VARCHAR(255),
                created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                CONSTRAINT uq_resource_qr_token_hash UNIQUE (token_hash)
            );
            """);

        jdbcTemplate.execute("""
            CREATE UNIQUE INDEX IF NOT EXISTS uq_resource_single_active_qr
                ON resource_qr(resource_id)
                WHERE status = 'ACTIVE';
            """);

        jdbcTemplate.execute("DELETE FROM resource_qr;");
        jdbcTemplate.execute("DELETE FROM resource;");

        // Seed tenant and app_user records
        jdbcTemplate.update("INSERT INTO tenant(id, name) VALUES (?, 'Test Tenant') ON CONFLICT (id) DO NOTHING", TENANT_ID);
        jdbcTemplate.update("INSERT INTO app_user(id, tenant_id, email) VALUES (?, ?, 'tester@venuenexus.test') ON CONFLICT (id) DO NOTHING", USER_ID, TENANT_ID);
    }

    private void ensureLocationExists(UUID locId) {
        jdbcTemplate.update("INSERT INTO location(id, tenant_id, name) VALUES (?, ?, 'Test Location') ON CONFLICT (id) DO NOTHING", locId, TENANT_ID);
    }

    @Test
    void testPostgreSqlJsonbCapabilitiesMapping() {
        UUID locId = UUID.randomUUID();
        ensureLocationExists(locId);
        String jsonCaps = "{\"seats\":6,\"outdoor\":true,\"smoking\":false}";

        var req = new CreateResourceRequest("T99", "Table 99", ResourceType.TABLE, locId, null, null, ActivationPolicy.INSTANT, jsonCaps);
        ResourceResponse created = resourceService.create(req);

        Resource entity = resourceRepository.findByIdAndTenantId(created.id(), TENANT_ID).orElseThrow();
        assertEquals("T99", entity.getResourceCode());

        // Verify JSONB stored in PostgreSQL
        String rawJson = jdbcTemplate.queryForObject(
                "SELECT capabilities::text FROM resource WHERE id = ?",
                String.class,
                created.id()
        );
        assertNotNull(rawJson);
        assertTrue(rawJson.contains("\"seats\": 6") || rawJson.contains("\"seats\":6"));
        assertTrue(rawJson.contains("outdoor"));
    }

    @Test
    void testUniqueResourceCodeWithinTenantAndLocationInPostgres() {
        UUID locId = UUID.randomUUID();
        ensureLocationExists(locId);
        var req1 = new CreateResourceRequest("CODE1", "Table 1", ResourceType.TABLE, locId, null, null, null, null);
        resourceService.create(req1);

        // Same tenant + location + code must fail with duplicate exception
        var req2 = new CreateResourceRequest("CODE1", "Table 1 Dup", ResourceType.TABLE, locId, null, null, null, null);
        assertThrows(RuntimeException.class, () -> resourceService.create(req2));

        // Same code in a different location must succeed
        UUID locId2 = UUID.randomUUID();
        ensureLocationExists(locId2);
        var req3 = new CreateResourceRequest("CODE1", "Table 1 Loc2", ResourceType.TABLE, locId2, null, null, null, null);
        assertDoesNotThrow(() -> resourceService.create(req3));
    }

    @Test
    void testPartialUniqueIndexEnforcesOneActiveQrPerResourceInPostgres() {
        UUID locId = UUID.randomUUID();
        ensureLocationExists(locId);
        var res = resourceService.create(new CreateResourceRequest("QR-TEST", "Table QR", ResourceType.TABLE, locId, null, null, null, null));

        // Insert first ACTIVE row
        UUID qr1Id = UUID.randomUUID();
        jdbcTemplate.update("""
            INSERT INTO resource_qr(id, resource_id, token_hash, version, status, issued_by_user_id, created_at)
            VALUES (?, ?, 'hash1111111111111111111111111111111111111111111111111111111111111111', 1, 'ACTIVE', ?, NOW())
            """, qr1Id, res.id(), USER_ID);

        // Attempting to insert a second ACTIVE row for the same resource must trigger uq_resource_single_active_qr violation
        UUID qr2Id = UUID.randomUUID();
        assertThrows(DataIntegrityViolationException.class, () -> {
            jdbcTemplate.update("""
                INSERT INTO resource_qr(id, resource_id, token_hash, version, status, issued_by_user_id, created_at)
                VALUES (?, ?, 'hash2222222222222222222222222222222222222222222222222222222222222222', 2, 'ACTIVE', ?, NOW())
                """, qr2Id, res.id(), USER_ID);
        });

        // Inserting a REVOKED row for the same resource must succeed (partial index ignores non-ACTIVE)
        UUID qr3Id = UUID.randomUUID();
        assertDoesNotThrow(() -> {
            jdbcTemplate.update("""
                INSERT INTO resource_qr(id, resource_id, token_hash, version, status, issued_by_user_id, created_at, revoked_at)
                VALUES (?, ?, 'hash3333333333333333333333333333333333333333333333333333333333333333', 1, 'REVOKED', ?, NOW(), NOW())
                """, qr3Id, res.id(), USER_ID);
        });
    }

    @Test
    void testAtomicReissueInPostgres() {
        UUID locId = UUID.randomUUID();
        ensureLocationExists(locId);
        var res = resourceService.create(new CreateResourceRequest("ATOMIC-T", "Table Atomic", ResourceType.TABLE, locId, null, null, null, null));

        // First issue: version 1
        QrIssueResponse v1 = qrService.issue(res.id());
        assertEquals(1, v1.qrVersion());

        // Reissue: must revoke v1 and create v2 in ONE transaction
        QrIssueResponse v2 = qrService.reissue(res.id());
        assertEquals(2, v2.qrVersion());

        // Verify exactly 1 ACTIVE row and 1 REVOKED row in PostgreSQL
        Integer activeCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM resource_qr WHERE resource_id = ? AND status = 'ACTIVE'",
                Integer.class,
                res.id()
        );
        Integer revokedCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM resource_qr WHERE resource_id = ? AND status = 'REVOKED'",
                Integer.class,
                res.id()
        );

        assertEquals(1, activeCount, "There must be exactly one active QR in PostgreSQL");
        assertEquals(1, revokedCount, "The previous QR must be marked REVOKED in PostgreSQL");
    }
}

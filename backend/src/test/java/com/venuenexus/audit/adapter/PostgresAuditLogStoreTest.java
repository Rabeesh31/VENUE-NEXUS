package com.venuenexus.audit.adapter;

import com.venuenexus.audit.model.AuditEvent;
import com.venuenexus.audit.model.AuditEventType;
import com.venuenexus.audit.model.AuditRecord;
import com.venuenexus.audit.port.AuditLogStore;
import com.venuenexus.audit.service.AuditDetailsSanitizer;
import com.venuenexus.audit.service.AuditLogService;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * PostgreSQL 16 Testcontainers integration test suite for database-level audit sequence counter.
 * Validates tests (a) through (g) per Team Lead (Rabeesh R) Phase 1 review note:
 *  a) Sequential progression 1, 2, 3
 *  b) 50+ concurrent threads for same tenant: gapless 1..N, no duplicates, valid chain
 *  c) Multiple independent tenants running concurrently: each chain starts at 1
 *  d) Platform chain (tenant_id NULL) running concurrently: gapless, valid
 *  e) Database unique constraint rejects direct duplicate (tenant, sequence) inserts
 *  f) Rolled back transaction leaves no gap and no half-written chain
 *  g) Tampering with stored row detected by verifyChain at exact sequence
 *
 * If Docker is not available in the execution environment, these tests are marked NOT VERIFIED
 * and skipped via JUnit 5 assumption.
 */
class PostgresAuditLogStoreTest {

    private static PostgreSQLContainer<?> postgres;
    private static HikariDataSource dataSource;
    private static JdbcTemplate jdbcTemplate;
    private static TransactionTemplate transactionTemplate;
    private static JdbcAuditLogStore store;
    private static AuditLogService auditLogService;

    @BeforeAll
    static void setUpAll() {
        if (!isDockerAvailable()) {
            return;
        }

        postgres = new PostgreSQLContainer<>("postgres:16-alpine");
        postgres.start();

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(postgres.getJdbcUrl());
        config.setUsername(postgres.getUsername());
        config.setPassword(postgres.getPassword());
        config.setMaximumPoolSize(65);
        config.setMinimumIdle(5);

        dataSource = new HikariDataSource(config);
        jdbcTemplate = new JdbcTemplate(dataSource);
        DataSourceTransactionManager txManager = new DataSourceTransactionManager(dataSource);
        transactionTemplate = new TransactionTemplate(txManager);

        // Apply test fixture DDL
        ResourceDatabasePopulator populator = new ResourceDatabasePopulator();
        populator.addScript(new ClassPathResource("schema-audit-test-fixture.sql"));
        populator.execute(dataSource);

        store = new JdbcAuditLogStore(jdbcTemplate);
        AuditDetailsSanitizer sanitizer = new AuditDetailsSanitizer();
        auditLogService = new AuditLogService(store, sanitizer, transactionTemplate);
    }

    @AfterAll
    static void tearDownAll() {
        if (dataSource != null) {
            dataSource.close();
        }
        if (postgres != null) {
            postgres.stop();
        }
    }

    @BeforeEach
    void cleanDatabase() {
        Assumptions.assumeTrue(isDockerAvailable(),
                "Docker is not available; marking PostgreSQL Testcontainers tests as NOT VERIFIED");
        if (store != null) {
            store.clear();
        }
    }

    private static boolean isDockerAvailable() {
        try {
            return DockerClientFactory.instance().isDockerAvailable();
        } catch (Throwable t) {
            return false;
        }
    }

    @Test
    @DisplayName("a) Sequential: first record gets 1, next gets 2, next gets 3")
    void testA_sequentialRecordsIncrementMonotonically() {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        AuditRecord r1 = auditLogService.record(AuditEvent.success(
                tenantId, userId, AuditEventType.AUTH_LOGIN, "trace-1", "127.0.0.1", Map.of("step", 1)
        ));
        AuditRecord r2 = auditLogService.record(AuditEvent.success(
                tenantId, userId, AuditEventType.QR_ISSUED, "trace-2", "127.0.0.1", Map.of("step", 2)
        ));
        AuditRecord r3 = auditLogService.record(AuditEvent.success(
                tenantId, userId, AuditEventType.QR_REVOKED, "trace-3", "127.0.0.1", Map.of("step", 3)
        ));

        assertThat(r1.sequence()).isEqualTo(1L);
        assertThat(r2.sequence()).isEqualTo(2L);
        assertThat(r3.sequence()).isEqualTo(3L);

        assertThat(r1.prevHash()).isEqualTo(AuditLogService.GENESIS_PREV_HASH);
        assertThat(r2.prevHash()).isEqualTo(r1.hash());
        assertThat(r3.prevHash()).isEqualTo(r2.hash());

        assertThat(auditLogService.verifyChain(tenantId)).isEmpty();
    }

    @Test
    @DisplayName("b) 50+ threads call record() for the same tenant at once: sequences are exactly 1..N, no duplicates, no gaps, verifyChain passes")
    void testB_fiftyThreadsCallRecordAtOnceNoDuplicatesNoGaps() throws InterruptedException {
        UUID tenantId = UUID.randomUUID();
        int threadCount = 55;

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        List<AuditRecord> results = new CopyOnWriteArrayList<>();
        List<Throwable> errors = new CopyOnWriteArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            final int idx = i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    AuditRecord rec = auditLogService.record(AuditEvent.success(
                            tenantId,
                            UUID.randomUUID(),
                            AuditEventType.AUTH_LOGIN,
                            "trace-concurrent-" + idx,
                            "10.0.0." + (idx % 250),
                            Map.of("threadIndex", idx)
                    ));
                    results.add(rec);
                } catch (Throwable t) {
                    errors.add(t);
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        boolean completed = doneLatch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(completed).isTrue();
        assertThat(errors).isEmpty();
        assertThat(results).hasSize(threadCount);

        // Extract and sort sequence numbers
        List<Long> sequences = results.stream().map(AuditRecord::sequence).sorted().toList();
        List<Long> expectedSequences = new ArrayList<>();
        for (long seq = 1; seq <= threadCount; seq++) {
            expectedSequences.add(seq);
        }

        assertThat(sequences).as("Sequences must be exactly 1..N with no duplicates and no gaps")
                .isEqualTo(expectedSequences);

        // Verify chain integrity in PostgreSQL
        assertThat(auditLogService.verifyChain(tenantId)).isEmpty();
        assertThat(store.countByTenant(tenantId)).isEqualTo(threadCount);
    }

    @Test
    @DisplayName("c) Two or more tenants at once: each chain independent and valid, each starts at 1")
    void testC_multipleTenantsRecordConcurrentlyWithIndependentChains() throws InterruptedException {
        UUID tenantA = UUID.randomUUID();
        UUID tenantB = UUID.randomUUID();
        int recordsPerTenant = 25;

        ExecutorService executor = Executors.newFixedThreadPool(recordsPerTenant * 2);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(recordsPerTenant * 2);

        List<AuditRecord> recordsA = new CopyOnWriteArrayList<>();
        List<AuditRecord> recordsB = new CopyOnWriteArrayList<>();

        for (int i = 0; i < recordsPerTenant; i++) {
            final int idx = i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    recordsA.add(auditLogService.record(AuditEvent.success(
                            tenantA, UUID.randomUUID(), AuditEventType.AUTH_LOGIN, "trace-a-" + idx, "10.1.1.1", Map.of()
                    )));
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    doneLatch.countDown();
                }
            });
            executor.submit(() -> {
                try {
                    startLatch.await();
                    recordsB.add(auditLogService.record(AuditEvent.success(
                            tenantB, UUID.randomUUID(), AuditEventType.QR_ISSUED, "trace-b-" + idx, "10.2.2.2", Map.of()
                    )));
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        boolean completed = doneLatch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(completed).isTrue();
        assertThat(recordsA).hasSize(recordsPerTenant);
        assertThat(recordsB).hasSize(recordsPerTenant);

        List<Long> seqA = recordsA.stream().map(AuditRecord::sequence).sorted().toList();
        List<Long> seqB = recordsB.stream().map(AuditRecord::sequence).sorted().toList();

        List<Long> expected = new ArrayList<>();
        for (long s = 1; s <= recordsPerTenant; s++) {
            expected.add(s);
        }

        assertThat(seqA).isEqualTo(expected);
        assertThat(seqB).isEqualTo(expected);

        assertThat(auditLogService.verifyChain(tenantA)).isEmpty();
        assertThat(auditLogService.verifyChain(tenantB)).isEmpty();
    }

    @Test
    @DisplayName("d) Platform chain (tenant_id NULL) at once: no duplicates, valid")
    void testD_platformChainConcurrentRecording() throws InterruptedException {
        int threadCount = 30;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        List<AuditRecord> records = new CopyOnWriteArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            final int idx = i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    records.add(auditLogService.record(AuditEvent.success(
                            null, // platform chain
                            UUID.randomUUID(),
                            AuditEventType.AUTH_LOGIN,
                            "trace-platform-" + idx,
                            "127.0.0.1",
                            Map.of("platformOp", idx)
                    )));
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        boolean completed = doneLatch.await(20, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(completed).isTrue();
        assertThat(records).hasSize(threadCount);

        List<Long> sequences = records.stream().map(AuditRecord::sequence).sorted().toList();
        List<Long> expected = new ArrayList<>();
        for (long s = 1; s <= threadCount; s++) {
            expected.add(s);
        }

        assertThat(sequences).isEqualTo(expected);
        assertThat(auditLogService.verifyChain(null)).isEmpty();
    }

    @Test
    @DisplayName("e) Direct duplicate insert of the same (tenant, sequence) is rejected by the database")
    void testE_databaseUniqueConstraintRejectsDuplicateSequence() {
        UUID tenantId = UUID.randomUUID();

        // 1. Insert record with sequence 1 directly
        jdbcTemplate.update("""
            INSERT INTO audit_log (id, tenant_id, user_id, sequence, event_type, outcome, trace_id, ip_hash, details, occurred_at, prev_hash, hash)
            VALUES (?, ?, ?, 1, 'AUTH_LOGIN', 'SUCCESS', 'trace-e1', 'ip-hash', '{}'::jsonb, ?, '0000000000000000000000000000000000000000000000000000000000000000', 'hash1')
            """, UUID.randomUUID(), tenantId, UUID.randomUUID(), Timestamp.from(Instant.now()));

        // 2. Direct duplicate insert for same tenant with same sequence 1 must fail at DB level
        assertThatThrownBy(() -> jdbcTemplate.update("""
            INSERT INTO audit_log (id, tenant_id, user_id, sequence, event_type, outcome, trace_id, ip_hash, details, occurred_at, prev_hash, hash)
            VALUES (?, ?, ?, 1, 'QR_ISSUED', 'SUCCESS', 'trace-e2', 'ip-hash', '{}'::jsonb, ?, 'hash1', 'hash2')
            """, UUID.randomUUID(), tenantId, UUID.randomUUID(), Timestamp.from(Instant.now())))
                .isInstanceOf(DataIntegrityViolationException.class);

        // 3. Test backstop unique index for platform chain (tenant_id IS NULL)
        jdbcTemplate.update("""
            INSERT INTO audit_log (id, tenant_id, user_id, sequence, event_type, outcome, trace_id, ip_hash, details, occurred_at, prev_hash, hash)
            VALUES (?, NULL, ?, 1, 'AUTH_LOGIN', 'SUCCESS', 'trace-p1', 'ip-hash', '{}'::jsonb, ?, '0000000000000000000000000000000000000000000000000000000000000000', 'hash1')
            """, UUID.randomUUID(), UUID.randomUUID(), Timestamp.from(Instant.now()));

        assertThatThrownBy(() -> jdbcTemplate.update("""
            INSERT INTO audit_log (id, tenant_id, user_id, sequence, event_type, outcome, trace_id, ip_hash, details, occurred_at, prev_hash, hash)
            VALUES (?, NULL, ?, 1, 'QR_ISSUED', 'SUCCESS', 'trace-p2', 'ip-hash', '{}'::jsonb, ?, 'hash1', 'hash2')
            """, UUID.randomUUID(), UUID.randomUUID(), Timestamp.from(Instant.now())))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("f) A rolled-back transaction leaves no gap and no half-written chain")
    void testF_rolledBackTransactionLeavesNoGapAndNoHalfWrittenChain() {
        UUID tenantId = UUID.randomUUID();

        // Transaction 1: Record sequence 1 normally
        AuditRecord r1 = auditLogService.record(AuditEvent.success(
                tenantId, UUID.randomUUID(), AuditEventType.AUTH_LOGIN, "trace-f1", "127.0.0.1", Map.of()
        ));
        assertThat(r1.sequence()).isEqualTo(1L);

        // Transaction 2: Simulated business failure rolled back
        try {
            transactionTemplate.execute(status -> {
                auditLogService.record(AuditEvent.success(
                        tenantId, UUID.randomUUID(), AuditEventType.QR_ISSUED, "trace-f2-failed", "127.0.0.1", Map.of()
                ));
                // Throw exception to trigger rollback
                throw new RuntimeException("Simulated business error rolling back transaction");
            });
        } catch (RuntimeException ignored) {
            // Expected rollback
        }

        // Verify that sequence 2 was NOT committed to audit_log
        List<AuditRecord> recordsAfterRollback = store.findAllByTenantOrderBySequenceAsc(tenantId);
        assertThat(recordsAfterRollback).hasSize(1);
        assertThat(recordsAfterRollback.get(0).sequence()).isEqualTo(1L);

        // Verify counter in audit_chain is also rolled back to 1
        AuditLogStore.ChainState chainState = store.lockChain(tenantId);
        assertThat(chainState.lastSequence()).isEqualTo(1L);

        // Transaction 3: Next action records sequence 2 without any gaps!
        AuditRecord r2 = auditLogService.record(AuditEvent.success(
                tenantId, UUID.randomUUID(), AuditEventType.QR_REVOKED, "trace-f3-success", "127.0.0.1", Map.of()
        ));

        assertThat(r2.sequence()).as("Sequence must be 2, proving no gap was left by the rolled back transaction")
                .isEqualTo(2L);
        assertThat(r2.prevHash()).isEqualTo(r1.hash());

        // Full chain verification
        assertThat(auditLogService.verifyChain(tenantId)).isEmpty();
        assertThat(store.countByTenant(tenantId)).isEqualTo(2);
    }

    @Test
    @DisplayName("g) Tampering with one stored row makes verifyChain return the correct broken sequence")
    void testG_tamperingStoredRowDetectedAtExactSequence() {
        UUID tenantId = UUID.randomUUID();

        for (int i = 1; i <= 5; i++) {
            auditLogService.record(AuditEvent.success(
                    tenantId, UUID.randomUUID(), AuditEventType.AUTH_LOGIN, "trace-g-" + i, "127.0.0.1", Map.of("step", i)
            ));
        }

        assertThat(auditLogService.verifyChain(tenantId)).isEmpty();

        // Tamper row at sequence 3 directly in the database
        jdbcTemplate.update("""
            UPDATE audit_log
            SET details = '{"step":3,"tampered":true}'::jsonb
            WHERE tenant_id = ? AND sequence = 3
            """, tenantId);

        Optional<Long> brokenSeq = auditLogService.verifyChain(tenantId);
        assertThat(brokenSeq).isPresent().contains(3L);
    }
}

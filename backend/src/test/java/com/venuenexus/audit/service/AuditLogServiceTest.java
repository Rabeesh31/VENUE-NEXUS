package com.venuenexus.audit.service;

import com.venuenexus.audit.adapter.InMemoryAuditLogStore;
import com.venuenexus.audit.model.AuditEvent;
import com.venuenexus.audit.model.AuditEventType;
import com.venuenexus.audit.model.AuditRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;

class AuditLogServiceTest {

    private InMemoryAuditLogStore store;
    private AuditDetailsSanitizer sanitizer;
    private AuditLogService auditLogService;

    @BeforeEach
    void setUp() {
        store = new InMemoryAuditLogStore();
        sanitizer = new AuditDetailsSanitizer();
        auditLogService = new AuditLogService(store, sanitizer);
    }

    @Test
    @DisplayName("Recording AUTH_LOGIN and QR_REVOKED establishes valid hash chain")
    void recordAndVerifyChain() {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        // 1. Record AUTH_LOGIN
        AuditEvent loginEvent = AuditEvent.success(
                tenantId,
                userId,
                AuditEventType.AUTH_LOGIN,
                "trace-login-001",
                "192.168.1.50",
                Map.of("loginMethod", "PASSWORD", "userRole", "VENUE_MANAGER")
        );
        AuditRecord record1 = auditLogService.record(loginEvent);

        assertThat(record1.sequence()).isEqualTo(1L);
        assertThat(record1.prevHash()).isEqualTo(AuditLogService.GENESIS_PREV_HASH);
        assertThat(record1.hash()).hasSize(64);
        assertThat(record1.ipHash()).hasSize(64);
        assertThat(record1.ipHash()).isNotEqualTo("192.168.1.50");

        // 2. Record QR_REVOKED
        AuditEvent qrRevokedEvent = AuditEvent.success(
                tenantId,
                userId,
                AuditEventType.QR_REVOKED,
                "trace-qr-002",
                "192.168.1.50",
                Map.of("resourceCode", "T12", "reason", "Damaged plaque")
        );
        AuditRecord record2 = auditLogService.record(qrRevokedEvent);

        assertThat(record2.sequence()).isEqualTo(2L);
        assertThat(record2.prevHash()).isEqualTo(record1.hash());
        assertThat(record2.hash()).hasSize(64);

        // 3. Verify chain
        Optional<Long> brokenSeq = auditLogService.verifyChain(tenantId);
        assertThat(brokenSeq).isEmpty();
    }

    @Test
    @DisplayName("Tampering with a record's details fails verification at the exact sequence")
    void tamperingRecordFailsAtExactSequence() {
        UUID tenantId = UUID.randomUUID();

        for (int i = 1; i <= 5; i++) {
            auditLogService.record(AuditEvent.success(
                    tenantId,
                    UUID.randomUUID(),
                    AuditEventType.AUTH_LOGIN,
                    "trace-" + i,
                    "10.0.0.1",
                    Map.of("step", i)
            ));
        }

        assertThat(auditLogService.verifyChain(tenantId)).isEmpty();

        // Tamper record at sequence 3 by replacing it in the underlying store
        List<AuditRecord> records = store.findAllByTenantOrderBySequenceAsc(tenantId);
        AuditRecord original3 = records.get(2);
        AuditRecord tampered3 = new AuditRecord(
                original3.id(),
                original3.tenantId(),
                original3.userId(),
                original3.sequence(),
                original3.eventType(),
                original3.outcome(),
                original3.traceId(),
                original3.ipHash(),
                "{\"step\":3,\"tampered\":true}", // Modified details!
                original3.occurredAt(),
                original3.prevHash(),
                original3.hash()
        );

        // Re-populate store with tampered row
        store.clear();
        for (AuditRecord r : records) {
            if (r.sequence() == 3L) {
                store.save(tampered3);
            } else {
                store.save(r);
            }
        }

        // Verification must detect tampering at sequence 3
        Optional<Long> brokenSeq = auditLogService.verifyChain(tenantId);
        assertThat(brokenSeq).isPresent().contains(3L);
    }

    @Test
    @DisplayName("Deleting a record from the chain creates a gap and is detected")
    void deletedRecordDetected() {
        UUID tenantId = UUID.randomUUID();

        for (int i = 1; i <= 4; i++) {
            auditLogService.record(AuditEvent.success(
                    tenantId,
                    UUID.randomUUID(),
                    AuditEventType.QR_ISSUED,
                    "trace-" + i,
                    "127.0.0.1",
                    Map.of("qrIndex", i)
            ));
        }

        // Delete sequence 2
        List<AuditRecord> records = store.findAllByTenantOrderBySequenceAsc(tenantId);
        store.clear();
        for (AuditRecord r : records) {
            if (r.sequence() != 2L) {
                store.save(r);
            }
        }

        Optional<Long> brokenSeq = auditLogService.verifyChain(tenantId);
        // Sequence gap at the position where 3 was found instead of 2
        assertThat(brokenSeq).isPresent().contains(3L);
    }

    @Test
    @DisplayName("Reordering records in the chain breaks linkage and is detected")
    void reorderedRecordsDetected() {
        UUID tenantId = UUID.randomUUID();

        auditLogService.record(AuditEvent.success(tenantId, UUID.randomUUID(), AuditEventType.AUTH_LOGIN, "t1", "127.0.0.1", Map.of()));
        auditLogService.record(AuditEvent.success(tenantId, UUID.randomUUID(), AuditEventType.QR_ISSUED, "t2", "127.0.0.1", Map.of()));
        auditLogService.record(AuditEvent.success(tenantId, UUID.randomUUID(), AuditEventType.QR_REVOKED, "t3", "127.0.0.1", Map.of()));

        List<AuditRecord> records = store.findAllByTenantOrderBySequenceAsc(tenantId);
        store.clear();
        // Save in order: 1, 3, 2
        store.save(records.get(0));
        store.save(records.get(2));
        store.save(records.get(1));

        Optional<Long> brokenSeq = auditLogService.verifyChain(tenantId);
        assertThat(brokenSeq).isPresent().contains(3L);
    }

    @Test
    @DisplayName("Concurrent record calls serialize per tenant and keep the chain valid")
    void concurrentRecordingKeepsChainValid() throws InterruptedException {
        UUID tenantId = UUID.randomUUID();
        int threadCount = 50;
        int operationsPerThread = 5;
        int totalEvents = threadCount * operationsPerThread;

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            final int threadIndex = i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    for (int j = 0; j < operationsPerThread; j++) {
                        auditLogService.record(AuditEvent.success(
                                tenantId,
                                UUID.randomUUID(),
                                (j % 2 == 0) ? AuditEventType.AUTH_LOGIN : AuditEventType.QR_REVOKED,
                                "trace-" + threadIndex + "-" + j,
                                "192.168.0." + threadIndex,
                                Map.of("thread", threadIndex, "op", j)
                        ));
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        boolean completed = doneLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(completed).isTrue();
        assertThat(store.countByTenant(tenantId)).isEqualTo(totalEvents);

        // Assert full chain verifies with zero broken links
        Optional<Long> brokenSeq = auditLogService.verifyChain(tenantId);
        assertThat(brokenSeq).isEmpty();
    }

    @Test
    @DisplayName("Sanitizer strips sensitive keys (password, token, secret, auth, qr, otp)")
    void sanitizerStripsSensitiveKeys() {
        Map<String, Object> sensitiveMap = Map.of(
                "username", "manager1",
                "password", "SecretPassword123!",
                "accessToken", "jwt.token.string",
                "qrToken", "128bitplaintoken",
                "otpCode", "123456",
                "authorization", "Bearer xyz",
                "nested", Map.of("secretKey", "super-secret", "safeData", "visible")
        );

        Map<String, Object> sanitized = sanitizer.sanitize(sensitiveMap);

        assertThat(sanitized).containsKey("username");
        assertThat(sanitized).doesNotContainKey("password");
        assertThat(sanitized).doesNotContainKey("accessToken");
        assertThat(sanitized).doesNotContainKey("qrToken");
        assertThat(sanitized).doesNotContainKey("otpCode");
        assertThat(sanitized).doesNotContainKey("authorization");

        @SuppressWarnings("unchecked")
        Map<String, Object> nested = (Map<String, Object>) sanitized.get("nested");
        assertThat(nested).containsKey("safeData");
        assertThat(nested).doesNotContainKey("secretKey");
    }
}

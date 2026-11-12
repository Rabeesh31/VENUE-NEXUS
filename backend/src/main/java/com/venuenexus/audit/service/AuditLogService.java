package com.venuenexus.audit.service;

import com.venuenexus.audit.model.AuditEvent;
import com.venuenexus.audit.model.AuditRecord;
import com.venuenexus.audit.port.AuditLogStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Append-only, tamper-evident hash-chained audit logging service.
 * Enforces per-tenant serialized sequence progression and SHA-256 chain verification.
 * In production/PostgreSQL, sequence counters are maintained in the audit_chain table
 * with row locks (SELECT ... FOR UPDATE) in a single database transaction.
 * Matches docs/security.md §6 and Step 1.5f.
 */
@Service
@Transactional
public class AuditLogService {

    private static final Logger logger = LoggerFactory.getLogger(AuditLogService.class);

    public static final String GENESIS_PREV_HASH = "0".repeat(64);

    private final AuditLogStore auditLogStore;
    private final AuditDetailsSanitizer sanitizer;
    private final TransactionTemplate transactionTemplate;

    public AuditLogService(AuditLogStore auditLogStore, AuditDetailsSanitizer sanitizer) {
        this(auditLogStore, sanitizer, (PlatformTransactionManager) null);
    }

    @Autowired
    public AuditLogService(AuditLogStore auditLogStore, AuditDetailsSanitizer sanitizer, @Nullable PlatformTransactionManager transactionManager) {
        this.auditLogStore = auditLogStore;
        this.sanitizer = sanitizer;
        this.transactionTemplate = (transactionManager != null) ? new TransactionTemplate(transactionManager) : null;
    }

    public AuditLogService(AuditLogStore auditLogStore, AuditDetailsSanitizer sanitizer, @Nullable TransactionTemplate transactionTemplate) {
        this.auditLogStore = auditLogStore;
        this.sanitizer = sanitizer;
        this.transactionTemplate = transactionTemplate;
    }

    /**
     * Records a new audit event into the tenant's hash chain.
     * Runs in ONE database transaction: locks the chain counter row, increments sequence,
     * calculates SHA-256 hash using last_hash as prev_hash, inserts audit_log, and updates audit_chain.
     * Simultaneous callers for the same tenant wait in line at the row lock, guaranteeing
     * gapless monotonic sequences (1, 2, 3...) and preventing chain forks.
     * Rollback undoes the counter advancement cleanly.
     */
    @Transactional
    public AuditRecord record(AuditEvent event) {
        if (event == null) {
            throw new IllegalArgumentException("AuditEvent must not be null");
        }

        if (transactionTemplate != null) {
            return transactionTemplate.execute(status -> doRecord(event));
        } else {
            return doRecord(event);
        }
    }

    private AuditRecord doRecord(AuditEvent event) {
        AuditLogStore.ChainState chainState = auditLogStore.lockChain(event.tenantId());
        try {
            long nextSequence = chainState.lastSequence() + 1L;
            String prevHash = (chainState.lastSequence() == 0L)
                    ? GENESIS_PREV_HASH
                    : chainState.lastHash();

            String sanitizedDetails = sanitizer.toJsonString(event.details());
            String ipHash = AuditDetailsSanitizer.hashIp(event.clientIp());

            String hash = calculateHash(
                    nextSequence,
                    event.tenantId(),
                    event.eventType().name(),
                    event.outcome(),
                    event.userId(),
                    event.traceId(),
                    ipHash,
                    sanitizedDetails,
                    DateTimeFormatter.ISO_INSTANT.format(event.occurredAt()),
                    prevHash
            );

            AuditRecord record = new AuditRecord(
                    UUID.randomUUID(),
                    event.tenantId(),
                    event.userId(),
                    nextSequence,
                    event.eventType(),
                    event.outcome(),
                    event.traceId(),
                    ipHash,
                    sanitizedDetails,
                    event.occurredAt(),
                    prevHash,
                    hash
            );

            auditLogStore.save(record);
            logger.debug("Audit record created: tenant={}, seq={}, event={}, hash={}",
                    event.tenantId(), nextSequence, event.eventType(), hash);

            return record;
        } catch (RuntimeException | Error e) {
            auditLogStore.releaseLock(event.tenantId());
            throw e;
        }
    }

    /**
     * Verifies the cryptographic integrity of an entire tenant's audit chain.
     * Recomputes every row's SHA-256 hash and checks previous-row linkages.
     *
     * @param tenantId Tenant ID to verify (null for platform chain)
     * @return Optional containing the first broken sequence number if compromised; empty if valid.
     */
    @Transactional(readOnly = true)
    public Optional<Long> verifyChain(UUID tenantId) {
        List<AuditRecord> chain = auditLogStore.findAllByTenantOrderBySequenceAsc(tenantId);
        if (chain.isEmpty()) {
            return Optional.empty();
        }

        long expectedSequence = 1L;
        String expectedPrevHash = GENESIS_PREV_HASH;

        for (AuditRecord record : chain) {
            // Check 1: Monotonic sequence without gaps
            if (record.sequence() != expectedSequence) {
                logger.warn("Audit chain sequence gap detected: expected={}, actual={}", expectedSequence, record.sequence());
                return Optional.of(record.sequence());
            }

            // Check 2: Previous hash linkage
            if (!expectedPrevHash.equalsIgnoreCase(record.prevHash())) {
                logger.warn("Audit chain prev_hash linkage broken at seq={}: expected={}, actual={}",
                        record.sequence(), expectedPrevHash, record.prevHash());
                return Optional.of(record.sequence());
            }

            // Check 3: Current row SHA-256 hash integrity
            String expectedHash = calculateHash(
                    record.sequence(),
                    record.tenantId(),
                    record.eventType().name(),
                    record.outcome(),
                    record.userId(),
                    record.traceId(),
                    record.ipHash(),
                    record.details(),
                    DateTimeFormatter.ISO_INSTANT.format(record.occurredAt()),
                    record.prevHash()
            );

            if (!expectedHash.equalsIgnoreCase(record.hash())) {
                logger.warn("Audit chain row hash corrupted at seq={}: expected={}, actual={}",
                        record.sequence(), expectedHash, record.hash());
                return Optional.of(record.sequence());
            }

            expectedPrevHash = record.hash();
            expectedSequence++;
        }

        return Optional.empty();
    }

    /**
     * Deterministic SHA-256 calculation over canonical representation.
     */
    public static String calculateHash(
            long sequence,
            UUID tenantId,
            String eventType,
            String outcome,
            UUID userId,
            String traceId,
            String ipHash,
            String details,
            String occurredAtIsoUtc,
            String prevHash
    ) {
        String canonical = String.join("|",
                String.valueOf(sequence),
                (tenantId != null) ? tenantId.toString() : "null",
                (eventType != null) ? eventType : "",
                (outcome != null) ? outcome : "",
                (userId != null) ? userId.toString() : "null",
                (traceId != null) ? traceId : "",
                (ipHash != null) ? ipHash : "null",
                (details != null) ? details : "{}",
                (occurredAtIsoUtc != null) ? occurredAtIsoUtc : "",
                (prevHash != null) ? prevHash : GENESIS_PREV_HASH
        );

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }
}

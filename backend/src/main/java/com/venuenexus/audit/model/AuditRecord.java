package com.venuenexus.audit.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Immutable hash-chained audit ledger entry.
 * Direct in-memory representation of table audit_log.
 */
public record AuditRecord(
        UUID id,
        UUID tenantId,
        UUID userId,
        long sequence,
        AuditEventType eventType,
        String outcome,
        String traceId,
        String ipHash,
        String details,
        Instant occurredAt,
        String prevHash,
        String hash
) {
}

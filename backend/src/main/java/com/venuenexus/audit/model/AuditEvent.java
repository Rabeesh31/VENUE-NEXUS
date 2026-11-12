package com.venuenexus.audit.model;

import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;

/**
 * Audit event request model submitted to AuditLogService.record(event).
 */
public record AuditEvent(
        UUID tenantId,
        UUID userId,
        AuditEventType eventType,
        String outcome,
        String traceId,
        String clientIp,
        Map<String, Object> details,
        Instant occurredAt
) {

    public AuditEvent {
        if (eventType == null) {
            throw new IllegalArgumentException("eventType must not be null");
        }
        if (outcome == null || outcome.isBlank()) {
            outcome = "SUCCESS";
        }
        if (occurredAt == null) {
            occurredAt = Instant.now();
        }
        details = (details != null) ? Map.copyOf(details) : Collections.emptyMap();
    }

    public static AuditEvent success(UUID tenantId, UUID userId, AuditEventType eventType, String traceId, String clientIp, Map<String, Object> details) {
        return new AuditEvent(tenantId, userId, eventType, "SUCCESS", traceId, clientIp, details, Instant.now());
    }

    public static AuditEvent failure(UUID tenantId, UUID userId, AuditEventType eventType, String traceId, String clientIp, Map<String, Object> details) {
        return new AuditEvent(tenantId, userId, eventType, "FAILURE", traceId, clientIp, details, Instant.now());
    }
}

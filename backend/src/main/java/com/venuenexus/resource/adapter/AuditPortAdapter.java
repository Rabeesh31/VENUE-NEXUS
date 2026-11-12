package com.venuenexus.resource.adapter;

import com.venuenexus.audit.model.AuditEvent;
import com.venuenexus.audit.model.AuditEventType;
import com.venuenexus.audit.service.AuditLogService;
import com.venuenexus.common.filter.TraceIdFilter;
import com.venuenexus.resource.port.Ports.AuditPort;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Production adapter linking Phase 5 AuditPort to Phase 1 AuditLogService.
 */
@Component
public class AuditPortAdapter implements AuditPort {

    private final AuditLogService auditLogService;

    public AuditPortAdapter(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    @Override
    public void record(String eventType, UUID tenantId, UUID actorUserId, Map<String, String> detail) {
        AuditEventType type;
        try {
            type = AuditEventType.valueOf(eventType);
        } catch (IllegalArgumentException e) {
            // Default to QR_REVOKED if unknown QR event, or fall back safely
            type = AuditEventType.QR_REVOKED;
        }

        Map<String, Object> detailsMap = (detail != null)
                ? new HashMap<>(detail)
                : Collections.emptyMap();

        String traceId = TraceIdFilter.getCurrentTraceId();
        if (traceId == null || traceId.isBlank()) {
            traceId = UUID.randomUUID().toString();
        }

        AuditEvent event = AuditEvent.success(
                tenantId,
                actorUserId,
                type,
                traceId,
                "127.0.0.1",
                detailsMap
        );

        auditLogService.record(event);
    }
}

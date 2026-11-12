package com.venuenexus.resource.domain;

/**
 * Physical resource operational status.
 * Matches Phase 2 V3 database check constraint:
 * CHECK (status IN ('AVAILABLE', 'OCCUPIED', 'RESERVED', 'MAINTENANCE', 'DISABLED'))
 */
public enum ResourceStatus {
    AVAILABLE,
    OCCUPIED,
    RESERVED,
    MAINTENANCE,
    DISABLED
}

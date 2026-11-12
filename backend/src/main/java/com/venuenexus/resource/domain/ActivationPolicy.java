package com.venuenexus.resource.domain;

/**
 * QR scan activation policy.
 * Matches Phase 2 V3 database check constraint:
 * CHECK (activation_policy IN ('INSTANT', 'STAFF_CONFIRM', 'OTP_ONLY'))
 */
public enum ActivationPolicy {
    INSTANT,
    STAFF_CONFIRM,
    OTP_ONLY
}

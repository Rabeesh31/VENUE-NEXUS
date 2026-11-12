package com.venuenexus.audit.model;

/**
 * Standard audit event types.
 * Matches docs/security.md §6.3 and Step 1.4.
 */
public enum AuditEventType {

    // --- Active Sprint 1 Events ---
    AUTH_LOGIN(true, "Staff user authenticated successfully."),
    QR_REVOKED(true, "QR identity revoked for a resource."),
    AUTH_LOGIN_FAILED(true, "Failed staff authentication attempt."),
    AUTH_LOGOUT(true, "Staff user signed out."),
    AUTH_REFRESH(true, "Staff refresh token rotated."),
    AUTH_REFRESH_REUSE_DETECTED(true, "Revoked refresh token presented; security breach mitigated."),
    USER_CREATED(true, "Staff user account created."),
    TENANT_CREATED(true, "New tenant provisioned."),
    QR_ISSUED(true, "QR identity issued for a resource."),
    QR_REISSUED(true, "QR identity revoked and reissued atomically."),

    // --- Reserved Events (CR-02, CR-03, CR-04/05 for Sprints 2-4) ---
    PAYMENT_SUCCEEDED(false, "Payment transaction captured successfully."),
    REFUND_REQUESTED(false, "Refund request initiated."),
    SESSION_ENDED(false, "Customer session closed."),
    AI_FEATURE_USED(false, "AI recommendation or chatbot feature invoked.");

    private final boolean activeInSprint1;
    private final String description;

    AuditEventType(boolean activeInSprint1, String description) {
        this.activeInSprint1 = activeInSprint1;
        this.description = description;
    }

    public boolean isActiveInSprint1() {
        return activeInSprint1;
    }

    public String getDescription() {
        return description;
    }
}

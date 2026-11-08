package com.venuenexus.resource.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * QR identity of a resource. Stores ONLY the SHA-256 hash of the token.
 * Matches Phase 2 V3 Flyway migration: table resource_qr.
 * DB guard: partial unique index on (resource_id) WHERE status = 'ACTIVE'.
 * Tenant isolation is preserved via the associated resource and authenticated context.
 */
@Entity
@Table(
    name = "resource_qr",
    uniqueConstraints = {
        @UniqueConstraint(name = "uq_resource_qr_token_hash", columnNames = {"token_hash"})
    }
)
public class ResourceQr {

    @Id
    private UUID id;

    @Column(name = "resource_id", nullable = false, updatable = false)
    private UUID resourceId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resource_id", insertable = false, updatable = false)
    private Resource resource;

    @Column(name = "token_hash", nullable = false, updatable = false, length = 64)
    private String tokenHash;

    @Column(name = "version", nullable = false, updatable = false)
    private int qrVersion;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    private QrStatus status = QrStatus.ACTIVE;

    @Column(name = "issued_by_user_id", updatable = false)
    private UUID issuedByUserId;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "revocation_reason", length = 255)
    private String revocationReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected ResourceQr() {
    }

    public ResourceQr(UUID id, UUID resourceId, String tokenHash, int qrVersion, UUID issuedByUserId) {
        this.id = id;
        this.resourceId = resourceId;
        this.tokenHash = tokenHash;
        this.qrVersion = qrVersion;
        this.issuedByUserId = issuedByUserId;
        this.createdAt = Instant.now();
    }

    public void revoke() {
        revoke(null);
    }

    public void revoke(String reason) {
        this.status = QrStatus.REVOKED;
        this.revokedAt = Instant.now();
        this.revocationReason = reason;
    }

    public UUID getId() { return id; }
    public UUID getResourceId() { return resourceId; }
    public Resource getResource() { return resource; }
    public int getQrVersion() { return qrVersion; }
    public QrStatus getStatus() { return status; }
    public UUID getIssuedByUserId() { return issuedByUserId; }
    public UUID getIssuedBy() { return issuedByUserId; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getIssuedAt() { return createdAt; }
    public Instant getRevokedAt() { return revokedAt; }
    public String getRevocationReason() { return revocationReason; }

    // Deliberately no tokenHash getter: nothing outside the repository query needs it,
    // and no toString() so the hash can never leak into logs.
}

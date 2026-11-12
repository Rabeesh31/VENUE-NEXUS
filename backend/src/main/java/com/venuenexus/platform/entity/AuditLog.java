package com.venuenexus.platform.entity;

import com.venuenexus.identity.entity.AppUser;
import com.venuenexus.venue.entity.Tenant;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * Append-only, tamper-evident hash-chained audit record.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(
    name = "audit_log",
    uniqueConstraints = {
        @UniqueConstraint(name = "uq_audit_tenant_sequence", columnNames = {"tenant_id", "sequence"})
    }
)
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "tenant_id")
    private UUID tenantId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", insertable = false, updatable = false)
    private Tenant tenant;

    @Column(name = "user_id")
    private UUID userId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", insertable = false, updatable = false)
    private AppUser user;

    @Column(name = "sequence", nullable = false)
    private Long sequence;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(name = "outcome", nullable = false)
    private String outcome; // 'SUCCESS', 'FAILURE', 'DENIED'

    @Column(name = "trace_id", nullable = false, length = 64)
    private String traceId;

    @Column(name = "ip_hash", length = 64)
    private String ipHash;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "details", nullable = false)
    private String details;

    @Column(name = "prev_hash", length = 64)
    private String prevHash;

    @Column(name = "hash", nullable = false, length = 64)
    private String hash;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private OffsetDateTime occurredAt;

    @PrePersist
    protected void onCreate() {
        if (this.occurredAt == null) {
            this.occurredAt = OffsetDateTime.now(ZoneOffset.UTC);
        }
    }

    // Aliases for compatibility
    public Long getTenantSequence() { return sequence; }
    public void setTenantSequence(Long tenantSequence) { this.sequence = tenantSequence; }
    public String getEventDetail() { return details; }
    public void setEventDetail(String eventDetail) { this.details = eventDetail; }
    public String getPreviousRowHash() { return prevHash; }
    public void setPreviousRowHash(String previousRowHash) { this.prevHash = previousRowHash; }
    public String getCurrentRowHash() { return hash; }
    public void setCurrentRowHash(String currentRowHash) { this.hash = currentRowHash; }
    public OffsetDateTime getCreatedAt() { return occurredAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.occurredAt = createdAt; }
}

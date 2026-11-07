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
        @UniqueConstraint(name = "uq_audit_tenant_sequence", columnNames = {"tenant_id", "tenant_sequence"})
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

    @Column(name = "tenant_sequence", nullable = false)
    private Long tenantSequence;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(name = "outcome", nullable = false)
    private String outcome; // 'SUCCESS', 'FAILURE', 'DENIED'

    @Column(name = "trace_id", nullable = false, length = 64)
    private String traceId;

    @Column(name = "ip_address_hash", length = 64)
    private String ipAddressHash;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "event_detail", nullable = false)
    private String eventDetail;

    @Column(name = "previous_row_hash", length = 64)
    private String previousRowHash;

    @Column(name = "current_row_hash", nullable = false, length = 64)
    private String currentRowHash;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = OffsetDateTime.now(ZoneOffset.UTC);
        }
    }
}

package com.venuenexus.platform.entity;

import com.venuenexus.common.entity.BaseAuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
import java.util.UUID;

/**
 * Idempotency record ensuring safe mutation repeats.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(
    name = "idempotency_record",
    uniqueConstraints = {
        @UniqueConstraint(name = "uq_idempotency_key_scope", columnNames = {"idempotency_key", "scope"})
    }
)
public class IdempotencyRecord extends BaseAuditableEntity {

    @Column(name = "idempotency_key", nullable = false)
    private String idempotencyKey;

    @Column(name = "scope", nullable = false)
    private String scope;

    @Column(name = "actor_id")
    private UUID actorId;

    @Column(name = "request_hash", nullable = false, length = 64)
    private String requestHash;

    @Column(name = "status", nullable = false)
    private String status; // 'IN_PROGRESS', 'COMPLETED', 'FAILED'

    @Column(name = "response_code")
    private Integer responseCode;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "response_body")
    private String responseBody;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;
}

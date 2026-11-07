package com.venuenexus.resource.entity;

import com.venuenexus.common.entity.BaseAuditableEntity;
import com.venuenexus.venue.entity.Department;
import com.venuenexus.venue.entity.Location;
import com.venuenexus.venue.entity.Space;
import com.venuenexus.venue.entity.Tenant;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.UUID;

/**
 * Physical venue resource entity (table, room, booth, bar seat).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(
    name = "resource",
    uniqueConstraints = {
        @UniqueConstraint(name = "uq_resource_tenant_location_code", columnNames = {"tenant_id", "location_id", "resource_code"})
    }
)
public class Resource extends BaseAuditableEntity {

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", insertable = false, updatable = false)
    private Tenant tenant;

    @Column(name = "location_id", nullable = false)
    private UUID locationId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "location_id", insertable = false, updatable = false)
    private Location location;

    @Column(name = "space_id")
    private UUID spaceId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "space_id", insertable = false, updatable = false)
    private Space space;

    @Column(name = "department_id")
    private UUID departmentId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id", insertable = false, updatable = false)
    private Department department;

    @Column(name = "resource_code", nullable = false)
    private String resourceCode;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Column(name = "resource_type", nullable = false)
    private String resourceType;

    @Column(name = "status", nullable = false)
    private String status; // 'AVAILABLE', 'OCCUPIED', 'RESERVED', 'MAINTENANCE', 'DISABLED'

    @Column(name = "activation_policy", nullable = false)
    private String activationPolicy; // 'INSTANT', 'STAFF_CONFIRM', 'OTP_ONLY'

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "capabilities")
    private String capabilities;
}

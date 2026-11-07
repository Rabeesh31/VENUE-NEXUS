package com.venuenexus.venue.entity;

import com.venuenexus.common.entity.BaseAuditableEntity;
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
 * Physical venue location entity scoped to Organisation and Tenant.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(
    name = "location",
    uniqueConstraints = {
        @UniqueConstraint(name = "uq_location_tenant_org_name", columnNames = {"tenant_id", "organisation_id", "name"})
    }
)
public class Location extends BaseAuditableEntity {

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", insertable = false, updatable = false)
    private Tenant tenant;

    @Column(name = "organisation_id", nullable = false)
    private UUID organisationId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organisation_id", insertable = false, updatable = false)
    private Organisation organisation;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "timezone", nullable = false)
    private String timezone;

    @Column(name = "currency", nullable = false)
    private String currency;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "config")
    private String config;
}

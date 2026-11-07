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

import java.util.UUID;

/**
 * Spatial hierarchy entity (BUILDING -> FLOOR -> ZONE).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(
    name = "space",
    uniqueConstraints = {
        @UniqueConstraint(name = "uq_space_location_parent_name", columnNames = {"location_id", "parent_space_id", "name"})
    }
)
public class Space extends BaseAuditableEntity {

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

    @Column(name = "parent_space_id")
    private UUID parentSpaceId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_space_id", insertable = false, updatable = false)
    private Space parentSpace;

    @Column(name = "space_type", nullable = false)
    private String spaceType; // 'BUILDING', 'FLOOR', 'ZONE'

    @Column(name = "name", nullable = false)
    private String name;
}

package com.venuenexus.identity.entity;

import com.venuenexus.common.entity.BaseAuditableEntity;
import com.venuenexus.venue.entity.Department;
import com.venuenexus.venue.entity.Location;
import com.venuenexus.venue.entity.Organisation;
import com.venuenexus.venue.entity.Tenant;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Staff user entity supporting platform admin and tenant/location scoping.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "app_user")
public class AppUser extends BaseAuditableEntity {

    @Column(name = "tenant_id")
    private UUID tenantId; // Nullable for PLATFORM_ADMIN

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", insertable = false, updatable = false)
    private Tenant tenant;

    @Column(name = "organisation_id")
    private UUID organisationId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organisation_id", insertable = false, updatable = false)
    private Organisation organisation;

    @Column(name = "location_id")
    private UUID locationId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "location_id", insertable = false, updatable = false)
    private Location location;

    @Column(name = "department_id")
    private UUID departmentId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id", insertable = false, updatable = false)
    private Department department;

    @Column(name = "email", nullable = false)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Column(name = "role", nullable = false)
    private String role; // PLATFORM_ADMIN, TENANT_ADMIN, VENUE_MANAGER, SUPERVISOR, STAFF

    @Column(name = "status", nullable = false)
    private String status; // ACTIVE, SUSPENDED, DEACTIVATED

    @Column(name = "is_available", nullable = false)
    private boolean available;

    @Column(name = "failed_login_count", nullable = false)
    private int failedLoginCount;

    @Column(name = "locked_until")
    private OffsetDateTime lockedUntil;

    @Column(name = "last_login_at")
    private OffsetDateTime lastLoginAt;
}

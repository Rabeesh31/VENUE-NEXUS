package com.venuenexus.resource.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Physical resource (table, room...). Table owned by Phase 2 (V3); mapping matches db-schema.md.
 * Unique constraint: (tenant_id, location_id, resource_code).
 */
@Entity
@Table(
    name = "resource",
    uniqueConstraints = {
        @UniqueConstraint(name = "uq_resource_tenant_location_code", columnNames = {"tenant_id", "location_id", "resource_code"})
    }
)
public class Resource {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "location_id", nullable = false, updatable = false)
    private UUID locationId;

    @Column(name = "space_id")
    private UUID spaceId;

    @Column(name = "department_id")
    private UUID departmentId;

    @Column(name = "resource_code", nullable = false, length = 100)
    private String resourceCode;

    @Column(name = "display_name", nullable = false, length = 255)
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(name = "resource_type", nullable = false, length = 50)
    private ResourceType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    private ResourceStatus status = ResourceStatus.AVAILABLE;

    @Enumerated(EnumType.STRING)
    @Column(name = "activation_policy", nullable = false, length = 50)
    private ActivationPolicy activationPolicy = ActivationPolicy.INSTANT;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "capabilities", columnDefinition = "jsonb")
    private String capabilities = "{}";

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected Resource() {
    }

    public Resource(UUID id, UUID tenantId, UUID locationId, String resourceCode, String displayName, ResourceType type) {
        this.id = id;
        this.tenantId = tenantId;
        this.locationId = locationId;
        this.resourceCode = resourceCode;
        this.displayName = displayName;
        this.type = type;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getLocationId() { return locationId; }
    public UUID getSpaceId() { return spaceId; }
    public void setSpaceId(UUID spaceId) { this.spaceId = spaceId; }
    public UUID getDepartmentId() { return departmentId; }
    public void setDepartmentId(UUID departmentId) { this.departmentId = departmentId; }
    public String getResourceCode() { return resourceCode; }
    public String getCode() { return resourceCode; }
    public void setResourceCode(String resourceCode) { this.resourceCode = resourceCode; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public ResourceType getType() { return type; }
    public void setType(ResourceType type) { this.type = type; }
    public ResourceStatus getStatus() { return status; }
    public void setStatus(ResourceStatus status) { this.status = status; }
    public ActivationPolicy getActivationPolicy() { return activationPolicy; }
    public void setActivationPolicy(ActivationPolicy p) { this.activationPolicy = p; }
    public String getCapabilities() { return capabilities; }
    public void setCapabilities(String capabilities) { this.capabilities = capabilities; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}

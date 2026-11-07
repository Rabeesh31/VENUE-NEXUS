-- ============================================================================
-- Flyway Migration V3: Physical Resource and QR Identity
-- Tables: resource, resource_qr
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 1. Table: resource
-- Description: Physical venue entities (e.g., table, private dining room, booth).
-- ----------------------------------------------------------------------------
CREATE TABLE resource (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
    location_id UUID NOT NULL REFERENCES location(id) ON DELETE CASCADE,
    space_id UUID REFERENCES space(id) ON DELETE SET NULL,
    department_id UUID REFERENCES department(id) ON DELETE SET NULL,
    resource_code VARCHAR(100) NOT NULL,
    display_name VARCHAR(255) NOT NULL,
    resource_type VARCHAR(50) NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'AVAILABLE' CHECK (status IN ('AVAILABLE', 'OCCUPIED', 'RESERVED', 'MAINTENANCE', 'DISABLED')),
    activation_policy VARCHAR(50) NOT NULL DEFAULT 'INSTANT' CHECK (activation_policy IN ('INSTANT', 'STAFF_CONFIRM', 'OTP_ONLY')),
    capabilities JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_resource_tenant_location_code UNIQUE (tenant_id, location_id, resource_code)
);

-- Indexes for scoping and operational filtering
CREATE INDEX idx_resource_tenant_location ON resource(tenant_id, location_id);
CREATE INDEX idx_resource_space ON resource(space_id);
CREATE INDEX idx_resource_department ON resource(department_id);
CREATE INDEX idx_resource_status ON resource(status);

-- ----------------------------------------------------------------------------
-- 2. Table: resource_qr
-- Description: QR tokens bound to resources. Stores SHA-256 hash only.
-- ----------------------------------------------------------------------------
CREATE TABLE resource_qr (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    resource_id UUID NOT NULL REFERENCES resource(id) ON DELETE CASCADE,
    token_hash VARCHAR(64) NOT NULL,
    version INT NOT NULL DEFAULT 1,
    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'REVOKED')),
    issued_by_user_id UUID REFERENCES app_user(id) ON DELETE SET NULL,
    revoked_at TIMESTAMPTZ,
    revocation_reason VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_resource_qr_token_hash UNIQUE (token_hash)
);

CREATE INDEX idx_resource_qr_resource_id ON resource_qr(resource_id);

-- Partial Unique Index: TRD Requirement - Enforces exactly ONE ACTIVE QR per resource
CREATE UNIQUE INDEX uq_resource_single_active_qr 
    ON resource_qr(resource_id) 
    WHERE status = 'ACTIVE';

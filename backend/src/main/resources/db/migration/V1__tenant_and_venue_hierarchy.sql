-- ============================================================================
-- Flyway Migration V1: Tenant and Venue Hierarchy
-- Tables: tenant, organisation, location, space, department
-- ============================================================================

-- Ensure pgcrypto or uuid extension is available for UUID generation if needed
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- ----------------------------------------------------------------------------
-- 1. Table: tenant
-- Description: Top-level entity representing a customer organization/client.
-- ----------------------------------------------------------------------------
CREATE TABLE tenant (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'SUSPENDED')),
    branding JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_tenant_status ON tenant(status);

-- ----------------------------------------------------------------------------
-- 2. Table: organisation
-- Description: Organizational unit within a tenant (e.g. brand, region).
-- ----------------------------------------------------------------------------
CREATE TABLE organisation (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'SUSPENDED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_organisation_tenant_name UNIQUE (tenant_id, name)
);

CREATE INDEX idx_organisation_tenant_id ON organisation(tenant_id);

-- ----------------------------------------------------------------------------
-- 3. Table: location
-- Description: Physical venue location (e.g., specific restaurant branch or hotel).
-- ----------------------------------------------------------------------------
CREATE TABLE location (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
    organisation_id UUID NOT NULL REFERENCES organisation(id) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL,
    timezone VARCHAR(100) NOT NULL DEFAULT 'UTC',
    currency VARCHAR(10) NOT NULL DEFAULT 'USD',
    config JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_location_tenant_org_name UNIQUE (tenant_id, organisation_id, name)
);

CREATE INDEX idx_location_tenant_id ON location(tenant_id);
CREATE INDEX idx_location_organisation_id ON location(organisation_id);

-- ----------------------------------------------------------------------------
-- 4. Table: space
-- Description: Physical spatial hierarchy (BUILDING -> FLOOR -> ZONE).
-- ----------------------------------------------------------------------------
CREATE TABLE space (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
    location_id UUID NOT NULL REFERENCES location(id) ON DELETE CASCADE,
    parent_space_id UUID REFERENCES space(id) ON DELETE CASCADE,
    space_type VARCHAR(50) NOT NULL CHECK (space_type IN ('BUILDING', 'FLOOR', 'ZONE')),
    name VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_space_location_parent_name UNIQUE (location_id, parent_space_id, name)
);

CREATE INDEX idx_space_tenant_id ON space(tenant_id);
CREATE INDEX idx_space_location_id ON space(location_id);
CREATE INDEX idx_space_parent_space_id ON space(parent_space_id);

-- ----------------------------------------------------------------------------
-- 5. Table: department
-- Description: Functional venue department (e.g., Kitchen, Bar, Housekeeping).
-- ----------------------------------------------------------------------------
CREATE TABLE department (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
    location_id UUID NOT NULL REFERENCES location(id) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL,
    department_type VARCHAR(100) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_department_tenant_location_name UNIQUE (tenant_id, location_id, name)
);

CREATE INDEX idx_department_tenant_id ON department(tenant_id);
CREATE INDEX idx_department_location_id ON department(location_id);

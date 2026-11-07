-- ============================================================================
-- Flyway Migration V2: Staff Identity and Token Management
-- Tables: app_user, refresh_token
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 1. Table: app_user
-- Description: Staff users, managers, and platform administrators.
-- Note: tenant_id is NULLABLE only for PLATFORM_ADMIN role.
-- ----------------------------------------------------------------------------
CREATE TABLE app_user (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID REFERENCES tenant(id) ON DELETE CASCADE,
    organisation_id UUID REFERENCES organisation(id) ON DELETE SET NULL,
    location_id UUID REFERENCES location(id) ON DELETE SET NULL,
    department_id UUID REFERENCES department(id) ON DELETE SET NULL,
    email VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    display_name VARCHAR(255) NOT NULL,
    role VARCHAR(50) NOT NULL CHECK (role IN ('PLATFORM_ADMIN', 'TENANT_ADMIN', 'VENUE_MANAGER', 'SUPERVISOR', 'STAFF')),
    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'SUSPENDED', 'DEACTIVATED')),
    is_available BOOLEAN NOT NULL DEFAULT TRUE,
    failed_login_count INT NOT NULL DEFAULT 0,
    locked_until TIMESTAMPTZ,
    last_login_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_tenant_required_for_non_platform_admin CHECK (
        (role = 'PLATFORM_ADMIN' AND tenant_id IS NULL) OR
        (role != 'PLATFORM_ADMIN' AND tenant_id IS NOT NULL)
    )
);

-- Case-insensitive unique index on email
CREATE UNIQUE INDEX uq_app_user_email_lower ON app_user(LOWER(email));

-- Supporting indexes for tenant, role, and hierarchy scoping queries
CREATE INDEX idx_app_user_tenant_role ON app_user(tenant_id, role);
CREATE INDEX idx_app_user_tenant_location ON app_user(tenant_id, location_id);
CREATE INDEX idx_app_user_department ON app_user(department_id);

-- ----------------------------------------------------------------------------
-- 2. Table: refresh_token
-- Description: Opaque refresh tokens with SHA-256 hashing, rotation, and family tracking.
-- ----------------------------------------------------------------------------
CREATE TABLE refresh_token (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    token_hash VARCHAR(64) NOT NULL,
    token_family UUID NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    replaced_by_token_id UUID REFERENCES refresh_token(id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_refresh_token_hash UNIQUE (token_hash)
);

CREATE INDEX idx_refresh_token_user_id ON refresh_token(user_id);
CREATE INDEX idx_refresh_token_family ON refresh_token(token_family);
CREATE INDEX idx_refresh_token_expires_at ON refresh_token(expires_at);

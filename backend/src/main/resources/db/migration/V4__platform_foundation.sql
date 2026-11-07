-- ============================================================================
-- Flyway Migration V4: Platform Foundations
-- Tables: audit_log, idempotency_record, outbox_event
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 1. Table: audit_log
-- Description: Append-only, tamper-evident hash-chained audit log.
-- ----------------------------------------------------------------------------
CREATE TABLE audit_log (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID REFERENCES tenant(id) ON DELETE SET NULL,
    user_id UUID REFERENCES app_user(id) ON DELETE SET NULL,
    tenant_sequence BIGINT NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    outcome VARCHAR(50) NOT NULL CHECK (outcome IN ('SUCCESS', 'FAILURE', 'DENIED')),
    trace_id VARCHAR(64) NOT NULL,
    ip_address_hash VARCHAR(64),
    event_detail JSONB NOT NULL,
    previous_row_hash VARCHAR(64),
    current_row_hash VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_audit_tenant_sequence UNIQUE (tenant_id, tenant_sequence)
);

CREATE INDEX idx_audit_log_tenant_id ON audit_log(tenant_id);
CREATE INDEX idx_audit_log_event_type ON audit_log(event_type);
CREATE INDEX idx_audit_log_trace_id ON audit_log(trace_id);
CREATE INDEX idx_audit_log_created_at ON audit_log(created_at);

-- ----------------------------------------------------------------------------
-- 2. Table: idempotency_record
-- Description: Ensures mutation requests (e.g. orders, payments) are executed once.
-- ----------------------------------------------------------------------------
CREATE TABLE idempotency_record (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    idempotency_key VARCHAR(255) NOT NULL,
    scope VARCHAR(100) NOT NULL,
    actor_id UUID,
    request_hash VARCHAR(64) NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'IN_PROGRESS' CHECK (status IN ('IN_PROGRESS', 'COMPLETED', 'FAILED')),
    response_code INT,
    response_body JSONB,
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_idempotency_key_scope UNIQUE (idempotency_key, scope)
);

CREATE INDEX idx_idempotency_expires_at ON idempotency_record(expires_at);

-- ----------------------------------------------------------------------------
-- 3. Table: outbox_event
-- Description: Transactional Outbox pattern for reliable async domain events.
-- ----------------------------------------------------------------------------
CREATE TABLE outbox_event (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID REFERENCES tenant(id) ON DELETE CASCADE,
    location_id UUID REFERENCES location(id) ON DELETE CASCADE,
    event_type VARCHAR(100) NOT NULL,
    aggregate_type VARCHAR(100) NOT NULL,
    aggregate_id VARCHAR(255) NOT NULL,
    payload JSONB NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'PUBLISHED', 'FAILED', 'DEAD_LETTER')),
    attempts INT NOT NULL DEFAULT 0,
    last_attempt_at TIMESTAMPTZ,
    published_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_outbox_event_status_created ON outbox_event(status, created_at);
CREATE INDEX idx_outbox_event_tenant_id ON outbox_event(tenant_id);

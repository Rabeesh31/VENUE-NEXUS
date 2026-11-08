-- =============================================================================
-- TEST FIXTURE ONLY: Audit Log Ledger and Audit Chain Counter Tables
-- =============================================================================
-- WARNING: This schema script is strictly a test fixture for Phase 1 testing.
-- It is NOT a production Flyway migration.
-- Flyway V4 migration does not yet exist on develop.
-- In Phase 2, Kavisharmitha will author the real Flyway V4 migration script
-- (e.g. src/main/resources/db/migration/V4__platform_foundations.sql)
-- incorporating these tables, constraints, and indexes as specified in docs/db-schema.md.
-- This test fixture MUST BE REPLACED by the real Flyway V4 migration when Phase 2 lands.
-- =============================================================================

-- Counter table: maintains per-tenant sequence counter and chain hash in PostgreSQL
CREATE TABLE IF NOT EXISTS audit_chain (
    chain_key UUID PRIMARY KEY,
    last_sequence BIGINT NOT NULL DEFAULT 0,
    last_hash TEXT NOT NULL
);

-- Append-only, tamper-evident hash-chained audit ledger
CREATE TABLE IF NOT EXISTS audit_log (
    id UUID PRIMARY KEY,
    tenant_id UUID NULL,
    user_id UUID NULL,
    sequence BIGINT NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    outcome VARCHAR(30) NOT NULL,
    trace_id VARCHAR(100) NOT NULL,
    ip_hash VARCHAR(64) NULL,
    details JSONB NOT NULL DEFAULT '{}'::jsonb,
    occurred_at TIMESTAMPTZ NOT NULL,
    prev_hash VARCHAR(64) NOT NULL,
    hash VARCHAR(64) NOT NULL
);

-- Backstop: Unique index on audit_log to guarantee sequence uniqueness per tenant
-- (coalescing tenant_id to all-zero UUID for platform-level events where tenant_id IS NULL)
CREATE UNIQUE INDEX IF NOT EXISTS uq_audit_log_tenant_seq
    ON audit_log (COALESCE(tenant_id, '00000000-0000-0000-0000-000000000000'::uuid), sequence);

-- Fast ordered traversal for chain verification and audit query
CREATE INDEX IF NOT EXISTS idx_audit_log_tenant_seq_asc
    ON audit_log (COALESCE(tenant_id, '00000000-0000-0000-0000-000000000000'::uuid), sequence ASC);

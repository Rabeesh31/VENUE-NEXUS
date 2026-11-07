# VENUE NEXUS — Database Schema & Relational Contracts (Sprint 1)

Document Version: 1.0  
Date: 7 November 2026  
Status: Approved Baseline  
Target Database: PostgreSQL 16  
Migration Tool: Flyway (Migrations V1 to V4)

---

## 1. Architecture & Multi-Tenancy Principles

1. **Shared Database, Shared Schema Multi-Tenancy**:
   - All tenant-owned tables contain `tenant_id UUID NOT NULL` (with platform-level exceptions strictly confined to `PLATFORM_ADMIN` users).
   - Foreign key relationships to parent entities enforce `ON DELETE CASCADE` within tenant boundaries.
2. **Tenant Scoping at Repository Layer**:
   - Multi-tenant repositories extend `TenantScopedRepository<T, ID>`.
   - Direct unscoped `findById` is forbidden on tenant-owned entities. Queries must explicitly include `tenantId`.
3. **Primary Keys & Timestamps**:
   - Primary keys use standard UUIDs (`gen_random_uuid()`).
   - Timestamps use `TIMESTAMPTZ` stored in UTC.
4. **Security & Data Confidentiality**:
   - Passwords and secret tokens are stored only as hashes (`password_hash` with BCrypt/Argon2, `token_hash` with SHA-256). Plaintext credentials are never persisted.

---

## 2. Relational Hierarchy Overview

```text
Tenant
 └── Organisation
      └── Location
           ├── Space (Building -> Floor -> Zone)
           ├── Department (Kitchen, Bar, etc.)
           └── Resource (Table, Room)
                └── ResourceQr (1 Active QR partial index)
```

---

## 3. Database Migration Definitions

### Flyway V1: Tenant and Venue Hierarchy (`V1__tenant_and_venue_hierarchy.sql`)

#### 1. `tenant`
- `id` (UUID, PK)
- `name` (VARCHAR(255), NOT NULL)
- `status` (VARCHAR(50), NOT NULL, CHECK: `'ACTIVE', 'SUSPENDED'`)
- `branding` (JSONB)
- `created_at`, `updated_at` (TIMESTAMPTZ, NOT NULL)

#### 2. `organisation`
- `id` (UUID, PK)
- `tenant_id` (UUID, FK -> `tenant.id` ON DELETE CASCADE, NOT NULL)
- `name` (VARCHAR(255), NOT NULL)
- `status` (VARCHAR(50), NOT NULL, CHECK: `'ACTIVE', 'SUSPENDED'`)
- `created_at`, `updated_at` (TIMESTAMPTZ, NOT NULL)
- **Constraint**: `UNIQUE (tenant_id, name)`

#### 3. `location`
- `id` (UUID, PK)
- `tenant_id` (UUID, FK -> `tenant.id` ON DELETE CASCADE, NOT NULL)
- `organisation_id` (UUID, FK -> `organisation.id` ON DELETE CASCADE, NOT NULL)
- `name` (VARCHAR(255), NOT NULL)
- `timezone` (VARCHAR(100), NOT NULL, DEFAULT `'UTC'`)
- `currency` (VARCHAR(10), NOT NULL, DEFAULT `'USD'`)
- `config` (JSONB)
- `created_at`, `updated_at` (TIMESTAMPTZ, NOT NULL)
- **Constraint**: `UNIQUE (tenant_id, organisation_id, name)`

#### 4. `space`
- `id` (UUID, PK)
- `tenant_id` (UUID, FK -> `tenant.id` ON DELETE CASCADE, NOT NULL)
- `location_id` (UUID, FK -> `location.id` ON DELETE CASCADE, NOT NULL)
- `parent_space_id` (UUID, FK -> `space.id` ON DELETE CASCADE, NULLABLE)
- `space_type` (VARCHAR(50), NOT NULL, CHECK: `'BUILDING', 'FLOOR', 'ZONE'`)
- `name` (VARCHAR(255), NOT NULL)
- `created_at`, `updated_at` (TIMESTAMPTZ, NOT NULL)
- **Constraint**: `UNIQUE (location_id, parent_space_id, name)`

#### 5. `department`
- `id` (UUID, PK)
- `tenant_id` (UUID, FK -> `tenant.id` ON DELETE CASCADE, NOT NULL)
- `location_id` (UUID, FK -> `location.id` ON DELETE CASCADE, NOT NULL)
- `name` (VARCHAR(255), NOT NULL)
- `department_type` (VARCHAR(100), NOT NULL)
- `created_at`, `updated_at` (TIMESTAMPTZ, NOT NULL)
- **Constraint**: `UNIQUE (tenant_id, location_id, name)`

---

### Flyway V2: Staff Identity (`V2__staff_identity.sql`)

#### 6. `app_user`
- `id` (UUID, PK)
- `tenant_id` (UUID, FK -> `tenant.id` ON DELETE CASCADE, NULLABLE only for `PLATFORM_ADMIN`)
- `organisation_id`, `location_id`, `department_id` (UUID, FKs ON DELETE SET NULL)
- `email` (VARCHAR(255), NOT NULL)
- `password_hash` (VARCHAR(255), NOT NULL)
- `display_name` (VARCHAR(255), NOT NULL)
- `role` (VARCHAR(50), NOT NULL, CHECK: `'PLATFORM_ADMIN', 'TENANT_ADMIN', 'VENUE_MANAGER', 'SUPERVISOR', 'STAFF'`)
- `status` (VARCHAR(50), NOT NULL, CHECK: `'ACTIVE', 'SUSPENDED', 'DEACTIVATED'`)
- `is_available` (BOOLEAN, NOT NULL DEFAULT TRUE)
- `failed_login_count` (INT, NOT NULL DEFAULT 0)
- `locked_until` (TIMESTAMPTZ)
- `last_login_at` (TIMESTAMPTZ)
- `created_at`, `updated_at` (TIMESTAMPTZ, NOT NULL)
- **Index**: Unique case-insensitive index `uq_app_user_email_lower ON app_user(LOWER(email))`
- **Check Constraint**: `(role = 'PLATFORM_ADMIN' AND tenant_id IS NULL) OR (role != 'PLATFORM_ADMIN' AND tenant_id IS NOT NULL)`

#### 7. `refresh_token`
- `id` (UUID, PK)
- `user_id` (UUID, FK -> `app_user.id` ON DELETE CASCADE, NOT NULL)
- `token_hash` (VARCHAR(64), UNIQUE, NOT NULL) — SHA-256 hash of opaque token
- `token_family` (UUID, NOT NULL) — Identifies token refresh chains for reuse detection
- `expires_at` (TIMESTAMPTZ, NOT NULL)
- `revoked_at` (TIMESTAMPTZ)
- `replaced_by_token_id` (UUID, FK -> `refresh_token.id` ON DELETE SET NULL)
- `created_at` (TIMESTAMPTZ, NOT NULL)

---

### Flyway V3: Resource and QR Identity (`V3__resource_and_qr.sql`)

#### 8. `resource`
- `id` (UUID, PK)
- `tenant_id` (UUID, FK -> `tenant.id` ON DELETE CASCADE, NOT NULL)
- `location_id` (UUID, FK -> `location.id` ON DELETE CASCADE, NOT NULL)
- `space_id`, `department_id` (UUID, FKs ON DELETE SET NULL)
- `resource_code` (VARCHAR(100), NOT NULL)
- `display_name` (VARCHAR(255), NOT NULL)
- `resource_type` (VARCHAR(50), NOT NULL)
- `status` (VARCHAR(50), NOT NULL, CHECK: `'AVAILABLE', 'OCCUPIED', 'RESERVED', 'MAINTENANCE', 'DISABLED'`)
- `activation_policy` (VARCHAR(50), NOT NULL, CHECK: `'INSTANT', 'STAFF_CONFIRM', 'OTP_ONLY'`)
- `capabilities` (JSONB)
- `created_at`, `updated_at` (TIMESTAMPTZ, NOT NULL)
- **Constraint**: `UNIQUE (tenant_id, location_id, resource_code)`

#### 9. `resource_qr`
- `id` (UUID, PK)
- `resource_id` (UUID, FK -> `resource.id` ON DELETE CASCADE, NOT NULL)
- `token_hash` (VARCHAR(64), UNIQUE, NOT NULL) — SHA-256 hash
- `version` (INT, NOT NULL DEFAULT 1)
- `status` (VARCHAR(50), NOT NULL, CHECK: `'ACTIVE', 'REVOKED'`)
- `issued_by_user_id` (UUID, FK -> `app_user.id` ON DELETE SET NULL)
- `revoked_at` (TIMESTAMPTZ)
- `revocation_reason` (VARCHAR(255))
- `created_at` (TIMESTAMPTZ, NOT NULL)
- **Partial Unique Index**:
  ```sql
  CREATE UNIQUE INDEX uq_resource_single_active_qr 
      ON resource_qr(resource_id) 
      WHERE status = 'ACTIVE';
  ```

---

### Flyway V4: Platform Foundations (`V4__platform_foundation.sql`)

#### 10. `audit_log`
- `id` (UUID, PK)
- `tenant_id` (UUID, FK -> `tenant.id` ON DELETE SET NULL)
- `user_id` (UUID, FK -> `app_user.id` ON DELETE SET NULL)
- `tenant_sequence` (BIGINT, NOT NULL)
- `event_type` (VARCHAR(100), NOT NULL)
- `outcome` (VARCHAR(50), NOT NULL, CHECK: `'SUCCESS', 'FAILURE', 'DENIED'`)
- `trace_id` (VARCHAR(64), NOT NULL)
- `ip_address_hash` (VARCHAR(64))
- `event_detail` (JSONB, NOT NULL)
- `previous_row_hash` (VARCHAR(64)) — Hash chaining
- `current_row_hash` (VARCHAR(64), NOT NULL) — Hash chaining
- `created_at` (TIMESTAMPTZ, NOT NULL)
- **Constraint**: `UNIQUE (tenant_id, tenant_sequence)`

#### 11. `idempotency_record`
- `id` (UUID, PK)
- `idempotency_key` (VARCHAR(255), NOT NULL)
- `scope` (VARCHAR(100), NOT NULL)
- `actor_id` (UUID)
- `request_hash` (VARCHAR(64), NOT NULL)
- `status` (VARCHAR(50), NOT NULL, CHECK: `'IN_PROGRESS', 'COMPLETED', 'FAILED'`)
- `response_code` (INT)
- `response_body` (JSONB)
- `expires_at` (TIMESTAMPTZ, NOT NULL)
- `created_at`, `updated_at` (TIMESTAMPTZ, NOT NULL)
- **Constraint**: `UNIQUE (idempotency_key, scope)`

#### 12. `outbox_event`
- `id` (UUID, PK)
- `tenant_id` (UUID, FK -> `tenant.id` ON DELETE CASCADE, NOT NULL)
- `location_id` (UUID, FK -> `location.id` ON DELETE CASCADE, NOT NULL)
- `event_type` (VARCHAR(100), NOT NULL)
- `aggregate_type` (VARCHAR(100), NOT NULL)
- `aggregate_id` (VARCHAR(255), NOT NULL)
- `payload` (JSONB, NOT NULL)
- `status` (VARCHAR(50), NOT NULL, CHECK: `'PENDING', 'PUBLISHED', 'FAILED', 'DEAD_LETTER'`)
- `attempts` (INT, NOT NULL DEFAULT 0)
- `last_attempt_at` (TIMESTAMPTZ)
- `published_at` (TIMESTAMPTZ)
- `created_at` (TIMESTAMPTZ, NOT NULL)

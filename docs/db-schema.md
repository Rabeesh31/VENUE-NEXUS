# VENUE NEXUS - Database Schema Specification (V1 to V4)

**Document Title:** Database Schema Specification (V1 to V4)  
**Version:** 0.2-DRAFT  
**Status:** DRAFT - awaiting approval by Rabeesh R  
**Owner:** Rithik Saran (Backend Lead) & Kavisharmitha (Phase 2 DB Lead)  
**Date:** 8 November 2026  

## Change Log
| Version | Date | Author | Description |
|---|---|---|---|
| 0.1-DRAFT | 6 November 2026 | Rithik Saran | Baseline draft specifying 12 tables across Flyway migrations V1–V4, constraints, indexes, audit_log requirements, and review items for Kavisharmitha. |
| 0.2-DRAFT | 8 November 2026 | Rithik Saran & Kavisharmitha | Added audit_chain counter table specification and backstop unique index on audit_log (COALESCE(tenant_id, all-zero UUID), sequence); updated Review Items for Kavisharmitha per Team Lead review note. |

---

## 1. Schema Conventions & Data Rules

1. **UUID Primary Keys:** All tables use UUID primary keys (`UUIDv7` recommended by schema specification; standard UUIDv4 acceptable).
2. **Tenant Partitioning:** Every tenant-owned table contains `tenant_id UUID NOT NULL` (with platform-level exceptions only where explicitly noted, e.g., `PLATFORM_ADMIN` users).
3. **UTC Timestamps:** All timestamps use `TIMESTAMPTZ` and are stored strictly in UTC (`createdAt`, `updatedAt`).
4. **Hashed Secrets:** Passwords, refresh tokens, and QR tokens are stored strictly as cryptographic hashes (BCrypt/Argon2 for passwords; SHA-256 for tokens). Plaintext secrets are forbidden in the database.
5. **No Hibernate DDL in Shared Environs:** `ddl-auto=none` is enforced. Schema changes are applied exclusively via forward-only Flyway migrations.
6. **No SQL in Phase 1:** Phase 1 defines this specification. Physical SQL migration files (`V1__...sql` to `V4__...sql`) are authored in Phase 2 by Kavisharmitha.

---

## 2. Table Specifications Across Migrations

### 2.1 Flyway V1: Tenant and Venue Hierarchy

#### Table 1: `tenant`
Represents an onboarded customer/business account.
- `id` UUID PRIMARY KEY
- `name` VARCHAR(150) NOT NULL
- `status` VARCHAR(30) NOT NULL — Permitted values: `ACTIVE`, `SUSPENDED`
- `branding` JSONB NULL — Optional logo URL, primary color, theme settings
- `created_at` TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
- `updated_at` TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
- *Indexes:* `idx_tenant_status` ON (`status`)

#### Table 2: `organisation`
Business grouping or brand inside a tenant.
- `id` UUID PRIMARY KEY
- `tenant_id` UUID NOT NULL REFERENCES `tenant(id)` ON DELETE RESTRICT
- `name` VARCHAR(150) NOT NULL
- `status` VARCHAR(30) NOT NULL DEFAULT 'ACTIVE'
- `created_at` TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
- `updated_at` TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
- *Constraints:* UNIQUE (`tenant_id`, `name`)
- *Indexes:* `idx_org_tenant` ON (`tenant_id`)

#### Table 3: `location`
Physical operating branch or site.
- `id` UUID PRIMARY KEY
- `tenant_id` UUID NOT NULL REFERENCES `tenant(id)` ON DELETE RESTRICT
- `organisation_id` UUID NOT NULL REFERENCES `organisation(id)` ON DELETE RESTRICT
- `name` VARCHAR(150) NOT NULL
- `time_zone` VARCHAR(50) NOT NULL — e.g., `Asia/Kolkata`, `UTC`
- `currency` VARCHAR(3) NOT NULL — ISO 4217, e.g., `INR`, `USD`
- `status` VARCHAR(30) NOT NULL DEFAULT 'ACTIVE'
- *Note:* Additional location settings (payment mode [CR-02], auto-logout trigger [CR-05]) deferred to post-V4 migrations per CR specifications.
- `created_at` TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
- `updated_at` TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
- *Indexes:* `idx_location_tenant_org` ON (`tenant_id`, `organisation_id`)

#### Table 4: `space`
Physical hierarchical areas within a location (Building -> Floor -> Zone).
- `id` UUID PRIMARY KEY
- `tenant_id` UUID NOT NULL REFERENCES `tenant(id)` ON DELETE RESTRICT
- `location_id` UUID NOT NULL REFERENCES `location(id)` ON DELETE RESTRICT
- `parent_id` UUID NULL REFERENCES `space(id)` ON DELETE CASCADE
- `type` VARCHAR(30) NOT NULL — Permitted values: `BUILDING`, `FLOOR`, `ZONE`
- `name` VARCHAR(100) NOT NULL
- `created_at` TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
- `updated_at` TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
- *Indexes:* `idx_space_loc_parent` ON (`tenant_id`, `location_id`, `parent_id`)

#### Table 5: `department`
Operating division within a location (kitchen, bar, housekeeping, etc.).
- `id` UUID PRIMARY KEY
- `tenant_id` UUID NOT NULL REFERENCES `tenant(id)` ON DELETE RESTRICT
- `location_id` UUID NOT NULL REFERENCES `location(id)` ON DELETE RESTRICT
- `name` VARCHAR(100) NOT NULL
- `type` VARCHAR(50) NOT NULL — e.g., `FLOOR_SERVICE`, `KITCHEN`, `BAR`, `HOUSEKEEPING`, `MAINTENANCE`, `FRONT_DESK`, `OTHER`
- `created_at` TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
- `updated_at` TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
- *Constraints:* UNIQUE (`tenant_id`, `location_id`, `name`)
- *Indexes:* `idx_department_loc` ON (`tenant_id`, `location_id`)

---

### 2.2 Flyway V2: Staff Identity and Token Management

#### Table 6: `app_user`
Staff accounts and platform administrative users.
- `id` UUID PRIMARY KEY
- `tenant_id` UUID NULL REFERENCES `tenant(id)` ON DELETE RESTRICT — Nullable *only* for `PLATFORM_ADMIN`
- `organisation_id` UUID NULL REFERENCES `organisation(id)` ON DELETE SET NULL
- `location_id` UUID NULL REFERENCES `location(id)` ON DELETE SET NULL
- `department_id` UUID NULL REFERENCES `department(id)` ON DELETE SET NULL
- `email` VARCHAR(255) NOT NULL — Case-insensitive unique
- `password_hash` VARCHAR(255) NOT NULL — BCrypt or Argon2id hash
- `display_name` VARCHAR(100) NOT NULL
- `role` VARCHAR(50) NOT NULL — `PLATFORM_ADMIN`, `TENANT_ADMIN`, `VENUE_MANAGER`, `SUPERVISOR`, `STAFF`
- `status` VARCHAR(30) NOT NULL DEFAULT 'ACTIVE' — `ACTIVE`, `SUSPENDED`, `LOCKED`
- `availability` VARCHAR(30) NOT NULL DEFAULT 'AVAILABLE'
- `failed_login_count` INT NOT NULL DEFAULT 0
- `lock_timestamp` TIMESTAMPTZ NULL
- `last_login_at` TIMESTAMPTZ NULL
- `created_at` TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
- `updated_at` TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
- *Constraints:* UNIQUE (LOWER(email))
- *Indexes:*
  - `idx_user_tenant_role` ON (`tenant_id`, `role`)
  - `idx_user_loc_dept` ON (`tenant_id`, `location_id`, `department_id`)

#### Table 7: `refresh_token`
Opaque hashed refresh tokens with family tracking for reuse detection.
- `id` UUID PRIMARY KEY
- `user_id` UUID NOT NULL REFERENCES `app_user(id)` ON DELETE CASCADE
- `token_hash` VARCHAR(64) NOT NULL — SHA-256 hex hash of plain token
- `family_id` UUID NOT NULL — Tracks token lineage for reuse detection
- `expiry_at` TIMESTAMPTZ NOT NULL
- `revoked_at` TIMESTAMPTZ NULL
- `replaced_by_hash` VARCHAR(64) NULL
- `created_at` TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
- *Constraints:* UNIQUE (`token_hash`)
- *Indexes:* `idx_refresh_user_family` ON (`user_id`, `family_id`)

---

### 2.3 Flyway V3: Resource Registry and QR Identity

#### Table 8: `resource`
Physical venue objects (tables, rooms, counters, seats, desks, beds, equipment).
- `id` UUID PRIMARY KEY
- `tenant_id` UUID NOT NULL REFERENCES `tenant(id)` ON DELETE RESTRICT
- `location_id` UUID NOT NULL REFERENCES `location(id)` ON DELETE RESTRICT
- `space_id` UUID NULL REFERENCES `space(id)` ON DELETE SET NULL
- `department_id` UUID NULL REFERENCES `department(id)` ON DELETE SET NULL
- `code` VARCHAR(50) NOT NULL — e.g. `T12`, `R305`
- `display_name` VARCHAR(100) NOT NULL — e.g. `Table 12 (Window)`
- `type` VARCHAR(50) NOT NULL — `TABLE`, `ROOM`, `COUNTER`, `SEAT`, `DESK`, `BED`, `EQUIPMENT`, `OTHER`
- `status` VARCHAR(30) NOT NULL DEFAULT 'AVAILABLE' — `AVAILABLE`, `OCCUPIED`, `OUT_OF_SERVICE`
- `activation_policy` VARCHAR(50) NOT NULL DEFAULT 'ALWAYS_ACTIVE'
- `capabilities` JSONB NOT NULL DEFAULT '{}'::jsonb — Structured attributes (e.g. `{"capacity": 4, "outdoor": true}`)
- `created_at` TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
- `updated_at` TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
- *Constraints:* UNIQUE (`tenant_id`, `location_id`, `code`)
- *Indexes:* `idx_resource_loc_space` ON (`tenant_id`, `location_id`, `space_id`)

#### Table 9: `resource_qr`
Cryptographically unguessable QR identities bound to physical resources.
- `id` UUID PRIMARY KEY
- `tenant_id` UUID NOT NULL REFERENCES `tenant(id)` ON DELETE RESTRICT
- `resource_id` UUID NOT NULL REFERENCES `resource(id)` ON DELETE CASCADE
- `token_hash` VARCHAR(64) NOT NULL — SHA-256 hash of 128-bit random token
- `version` INT NOT NULL DEFAULT 1
- `status` VARCHAR(30) NOT NULL DEFAULT 'ACTIVE' — `ACTIVE`, `REVOKED`
- `issuing_user_id` UUID NOT NULL REFERENCES `app_user(id)` ON DELETE RESTRICT
- `revoked_at` TIMESTAMPTZ NULL
- `created_at` TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
- *Constraints:*
  - UNIQUE (`token_hash`)
  - Partial Unique Index enforcing **one active QR per resource**:
    `CREATE UNIQUE INDEX uq_resource_active_qr ON resource_qr (resource_id) WHERE status = 'ACTIVE';`
- *Indexes:* `idx_qr_token_hash` ON (`token_hash`)

---

### 2.4 Flyway V4: Platform Foundations (Audit, Idempotency, Outbox)

#### Table 10: `audit_chain` [PROPOSED]
Maintains the sequence counter and cryptographic chain tip per tenant at the database level.
- `chain_key` UUID PRIMARY KEY — Tenant ID; or fixed all-zero UUID (`00000000-0000-0000-0000-000000000000`) for the platform chain (`tenant_id IS NULL`)
- `last_sequence` BIGINT NOT NULL DEFAULT 0 — Last allocated monotonic sequence number (starts at 0 before any events)
- `last_hash` TEXT NOT NULL — SHA-256 hash of the latest committed audit record in the chain (genesis: 64 hexadecimal zeros)
- *Concurrency & Locking Rule:* Every call to `record()` runs in ONE database transaction:
  1. `INSERT INTO audit_chain (chain_key, last_sequence, last_hash) VALUES (?, 0, '000...000') ON CONFLICT (chain_key) DO NOTHING;`
  2. `SELECT last_sequence, last_hash FROM audit_chain WHERE chain_key = ? FOR UPDATE;` (acquires an exclusive row lock on the tenant's chain row)
  3. Next sequence = `last_sequence + 1`
  4. Compute row hash using `last_hash` as `prev_hash`
  5. `INSERT INTO audit_log (...)`
  6. `UPDATE audit_chain SET last_sequence = ?, last_hash = ? WHERE chain_key = ?;`
  Simultaneous callers for the same tenant wait in line at the row lock, guaranteeing gapless monotonic sequences (1, 2, 3...) without duplicates or chain forking. A transaction rollback reverts the counter advancement cleanly.
- *Anti-Pattern Prohibited:* A PostgreSQL `SEQUENCE` object must **not** be used (causes unrecoverable sequence gaps on rollbacks and does not partition per tenant).

#### Table 11: `audit_log`
Append-only, tamper-evident hash-chained audit ledger.
- `id` UUID PRIMARY KEY
- `tenant_id` UUID NULL REFERENCES `tenant(id)` ON DELETE RESTRICT — Nullable *only* for platform-level events
- `user_id` UUID NULL REFERENCES `app_user(id)` ON DELETE SET NULL
- `sequence` BIGINT NOT NULL — Monotonically increasing per-tenant sequence number (starts at 1)
- `event_type` VARCHAR(100) NOT NULL — e.g. `AUTH_LOGIN`, `QR_REVOKED`
- `outcome` VARCHAR(30) NOT NULL — `SUCCESS`, `FAILURE`
- `trace_id` VARCHAR(100) NOT NULL
- `ip_hash` VARCHAR(64) NULL — SHA-256 hash of client IP
- `details` JSONB NOT NULL DEFAULT '{}'::jsonb — Sanitized metadata without secrets
- `occurred_at` TIMESTAMPTZ NOT NULL
- `prev_hash` VARCHAR(64) NOT NULL — SHA-256 hash of previous row in chain (genesis: 64 zeros)
- `hash` VARCHAR(64) NOT NULL — SHA-256 hash of canonical event string + prev_hash
- *Backstop Constraint & Indexes:*
  - Backstop Unique Index:
    `CREATE UNIQUE INDEX uq_audit_log_tenant_seq ON audit_log (COALESCE(tenant_id, '00000000-0000-0000-0000-000000000000'::uuid), sequence);`
  - Traversal Index:
    `CREATE INDEX idx_audit_log_tenant_seq_asc ON audit_log (COALESCE(tenant_id, '00000000-0000-0000-0000-000000000000'::uuid), sequence ASC);`
- *Security Privilege:* The application database user must be granted strictly `INSERT` and `SELECT` privileges on `audit_log` (no `UPDATE`, no `DELETE`).

#### Table 12: `idempotency_record`
Guarantees at-most-once processing for mutating operations.
- `id` UUID PRIMARY KEY
- `idempotency_key` VARCHAR(255) NOT NULL
- `scope` VARCHAR(100) NOT NULL — e.g., `tenant:{tenant_id}:resource`
- `actor_type` VARCHAR(50) NOT NULL — `STAFF`, `CUSTOMER`, `SYSTEM`
- `actor_id` VARCHAR(100) NOT NULL
- `request_hash` VARCHAR(64) NOT NULL — SHA-256 hash of request body
- `processing_status` VARCHAR(30) NOT NULL — `PROCESSING`, `COMPLETED`, `FAILED`
- `stored_response` JSONB NULL
- `expiry_at` TIMESTAMPTZ NOT NULL
- `created_at` TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
- *Constraints:* UNIQUE (`scope`, `idempotency_key`)
- *Indexes:* `idx_idempotency_expiry` ON (`expiry_at`)

#### Table 13: `outbox_event`
Transactional outbox ensuring at-least-once domain event publication.
- `id` UUID PRIMARY KEY
- `tenant_id` UUID NULL REFERENCES `tenant(id)` ON DELETE RESTRICT
- `location_id` UUID NULL REFERENCES `location(id)` ON DELETE RESTRICT
- `event_type` VARCHAR(100) NOT NULL
- `aggregate_type` VARCHAR(100) NOT NULL
- `aggregate_id` VARCHAR(100) NOT NULL
- `payload` JSONB NOT NULL
- `publication_status` VARCHAR(30) NOT NULL DEFAULT 'PENDING' — `PENDING`, `PUBLISHED`, `FAILED`
- `attempts` INT NOT NULL DEFAULT 0
- `published_at` TIMESTAMPTZ NULL
- `created_at` TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
- *Indexes:* `idx_outbox_pending` ON (`publication_status`, `created_at`) WHERE publication_status = 'PENDING'

---

## 3. Review Items for Kavisharmitha (Phase 2 Lead)

Kavisharmitha is responsible for authoring the physical Flyway SQL migrations (`V1__...sql` to `V4__...sql`) and JPA entity mappings in Phase 2. Please review and ensure the following points are incorporated:

1. **`audit_chain` Counter Table & Sequence Locking:**
   - In Flyway V4, author the `audit_chain` table:
     ```sql
     CREATE TABLE audit_chain (
         chain_key UUID PRIMARY KEY,
         last_sequence BIGINT NOT NULL DEFAULT 0,
         last_hash TEXT NOT NULL
     );
     ```
   - Do **NOT** use a PostgreSQL `SEQUENCE` object. Sequences increment outside transactions, generating unrecoverable gaps on rollbacks and lacking per-tenant isolation.
   - The application manages sequence progression by locking the chain row with `SELECT ... FOR UPDATE` in a single transaction, computing hash, inserting `audit_log`, and updating `audit_chain`.
2. **`audit_log` Backstop Unique Index:**
   - Enforce sequence uniqueness per tenant across both tenant and platform chains using a single COALESCE expression index:
     ```sql
     CREATE UNIQUE INDEX uq_audit_log_tenant_seq
         ON audit_log (COALESCE(tenant_id, '00000000-0000-0000-0000-000000000000'::uuid), sequence);
     ```
   - Add traversal index:
     ```sql
     CREATE INDEX idx_audit_log_tenant_seq_asc
         ON audit_log (COALESCE(tenant_id, '00000000-0000-0000-0000-000000000000'::uuid), sequence ASC);
     ```
3. **Replace Phase 1 Test Fixture with Flyway V4:**
   - Note that Flyway V4 does not exist on `develop` during Phase 1. For Phase 1 testing, a clearly labelled test fixture was authored at `src/test/resources/schema-audit-test-fixture.sql`.
   - When authoring Flyway migration `V4__platform_foundations.sql` in Phase 2, Kavisharmitha must match these exact table definitions and replace the test fixture with the production Flyway migration.
4. **`audit_log` Table Permissions:**
   - In production and migration provisioning scripts, ensure `GRANT INSERT, SELECT ON audit_log TO venue_app_user;` and strictly withhold `UPDATE` and `DELETE`.
   - Ensure `GRANT INSERT, SELECT, UPDATE ON audit_chain TO venue_app_user;` (withholding `DELETE`).
5. **`resource_qr` Partial Unique Index:**
   - Ensure the partial index `WHERE status = 'ACTIVE'` is implemented exactly as specified so reissuing a QR can revoke the old record and insert the new active record within the same transaction:
     `CREATE UNIQUE INDEX uq_resource_active_qr ON resource_qr (resource_id) WHERE status = 'ACTIVE';`
6. **Case-Insensitive Email Uniqueness:**
   - Use `CREATE UNIQUE INDEX uq_app_user_email_lower ON app_user (LOWER(email));`
7. **No SQL in Phase 1:**
   - Phase 1 does not commit production Flyway `.sql` migration files. All physical DDL migration scripts are owned by Phase 2.

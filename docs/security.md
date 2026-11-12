# VENUE NEXUS - Security, Authentication and Authorization Specification

**Document Title:** Security, Authentication and Authorization Specification  
**Version:** 0.2-DRAFT  
**Status:** DRAFT - awaiting approval by Rabeesh R  
**Owner:** Rithik Saran (Backend Lead)  
**Date:** 8 November 2026  

## Change Log
| Version | Date | Author | Description |
|---|---|---|---|
| 0.1-DRAFT | 6 November 2026 | Rithik Saran | Baseline draft of RBAC matrix, token lifecycle, QR cryptography, audit hash chain, logging rules, CR-02 payment/refund roles, and CR-03 AI data-handling policies. |
| 0.2-DRAFT | 8 November 2026 | Rithik Saran | Updated §6.1 Audit Chain description to specify database-level counter table audit_chain, row locking (SELECT FOR UPDATE) in a single transaction, rollback behavior, and backstop unique index per Team Lead review note. |

---

## 1. Security Architecture Principles

1. **Deny-by-Default:** Every endpoint and method is inaccessible unless explicitly granted by security configuration (`SecurityConfig`) or `@PreAuthorize`.
2. **Backend as Sole Authority:** UI visibility controls (e.g. hiding a button) are user experience affordances only. The backend validates and authorises every request.
3. **Tenant & Scope Derivation:** Tenant and location scopes are derived strictly from the validated JWT security context (`TenantContext`, `ScopeContext`), never from client-supplied URL paths, query parameters, or request bodies.
4. **Zero Secrets in Git / Logs:** Passwords, tokens, QR plain values, API keys, and credentials are never stored in version control or emitted to application logs.

---

## 2. Role-Based Access Control (RBAC) Matrix

The platform roles defined in the PRD are:
- `PLATFORM_ADMIN`: Operates the platform across all tenants; creates/suspends tenants.
- `TENANT_ADMIN`: Administers organizations, locations, and staff inside a specific tenant.
- `VENUE_MANAGER`: Operates a venue location: manages hierarchy, physical resources, and QR identities.
- `SUPERVISOR`: Oversees shifts or departments; restricted to permitted location scope.
- `STAFF`: Day-to-day operations; restricted to permitted location and department scope.

> **Governance Note:** The role list and permission matrix are **STILL SUBJECT TO MENTOR APPROVAL**.

### Capability Matrix [PROPOSED where marked]

| Capability / Permission | Scope Level | `PLATFORM_ADMIN` | `TENANT_ADMIN` | `VENUE_MANAGER` | `SUPERVISOR` | `STAFF` |
|---|---|:---:|:---:|:---:|:---:|:---:|
| `TENANT_CREATE` | Platform | ✅ | ❌ | ❌ | ❌ | ❌ |
| `TENANT_SUSPEND_RESUME`| Platform | ✅ | ❌ | ❌ | ❌ | ❌ |
| `ORGANISATION_MANAGE` | Tenant | ❌ | ✅ | ❌ | ❌ | ❌ |
| `LOCATION_MANAGE` | Tenant / Org | ❌ | ✅ | ❌ | ❌ | ❌ |
| `SPACE_HIERARCHY_MANAGE`| Location | ❌ | ✅ | ✅ | ❌ | ❌ |
| `DEPARTMENT_MANAGE` | Location | ❌ | ✅ | ✅ | ❌ | ❌ |
| `STAFF_USER_MANAGE` | Tenant / Loc | ❌ | ✅ | ✅ (Loc) | ❌ | ❌ |
| `RESOURCE_MANAGE` | Location | ❌ | ✅ | ✅ | ❌ | ❌ |
| `RESOURCE_VIEW` | Location / Dept | ❌ | ✅ | ✅ | ✅ | ✅ |
| `QR_ISSUE` | Location | ❌ | ✅ | ✅ | ❌ | ❌ |
| `QR_PRINT` | Location | ❌ | ✅ | ✅ | ✅ | ❌ |
| `QR_REVOKE` | Location | ❌ | ✅ | ✅ | ❌ | ❌ |
| `QR_REISSUE` | Location | ❌ | ✅ | ✅ | ❌ | ❌ |
| `AUDIT_VIEW` [PROPOSED] | Tenant / Loc | ✅ (Platform) | ✅ | ✅ (Loc) | ❌ | ❌ |
| `PAYMENT_VIEW` [CR-02] | Tenant / Loc | *Pending* | *Pending* | *Pending* | *Pending* | *Pending* |
| `REFUND_REQUEST` [CR-02] | Location | *Pending* | *Pending* | *Pending* | *Pending* | *Pending* |
| `REFUND_APPROVE` [CR-02] | Location | *Pending* | *Pending* | *Pending* | *Pending* | *Pending* |
| `AI_ADMIN` [CR-03] | Tenant | *Pending* | *Pending* | *Pending* | *Pending* | *Pending* |

*(Rows for `PAYMENT_VIEW`, `REFUND_REQUEST`, `REFUND_APPROVE`, and `AI_ADMIN` are PROPOSED for CR-02 and CR-03; role assignments are Pending Team Lead and mentor confirmation under D-5 and D-6).*

---

## 3. Staff Token Lifecycle & Specifications

### 3.1 Staff Access Token (JWT)
- **Token Format:** Signed JSON Web Token (`JWS`) using HMAC-SHA256 (`HS256`).
- **Lifetime:** Strictly 15 minutes (`900 seconds`).
- **Secret Key:** Injected strictly via `JWT_SECRET` environment variable (minimum 256 bits).
- **Claims Structure [PROPOSED]:**
  - `sub`: User ID (`UUID`)
  - `uid`: User ID (`UUID`)
  - `typ`: `STAFF_ACCESS` (rejects tokens if not matching)
  - `rol`: Role enum string (e.g. `VENUE_MANAGER`)
  - `tid`: Tenant ID (`UUID`, null for `PLATFORM_ADMIN`)
  - `oid`: Organisation ID (`UUID`, nullable)
  - `lid`: Location ID (`UUID`, nullable)
  - `did`: Department ID (`UUID`, nullable)
  - `jti`: JWT unique ID (`UUID`)
  - `iss`: `venue-nexus-auth`
  - `aud`: `venue-nexus-api`
  - `iat`: Issued-at epoch seconds
  - `exp`: Expiration epoch seconds
- **Validation Rules:**
  - Tokens with invalid signature, expired timestamp, incorrect `typ`, or wrong `aud` are rejected with HTTP 401.

### 3.2 Opaque Refresh Token
- **Format:** Cryptographically secure 256-bit random string (base64url encoded).
- **Storage Rule:** The plain refresh token is returned to the client **once**. Only the **SHA-256 hash** of the token is stored in the database (`refresh_token.token_hash`).
- **Lifetime:** 7 days (`604800 seconds`).
- **Rotation:** Every refresh request invalidates the presenting refresh token and issues a new token pair.
- **Family Reuse Detection:** Each token belongs to a `family_id`. If an already-rotated or revoked token is presented, the system flags a potential theft, immediately revokes the **entire token family**, records an audit alert, and responds with `AUTH_REFRESH_REUSE_DETECTED`.

### 3.3 Token Revocation & Logout
- Staff logout places the access token's `jti` onto an in-memory / Redis denylist with a TTL matching the token's remaining lifetime.
- The corresponding refresh token record is marked `revoked_at = CURRENT_TIMESTAMP`.

---

## 4. Password Hashing & Account Lockout

1. **Password Hashing [PROPOSED]:**
   - **Recommended Choice:** Argon2id (`memory=65536 KiB`, `iterations=3`, `parallelism=1`).
   - **Alternative:** BCrypt with a minimum work factor of 12.
   - Plaintext passwords and reversible encryptions are strictly forbidden.
2. **Account Lockout Policy [PROPOSED]:**
   - **Threshold:** 5 consecutive failed login attempts.
   - **Action:** Account status updated to `LOCKED` with `lock_timestamp = CURRENT_TIMESTAMP`.
   - **Lockout Duration:** 15 minutes. After 15 minutes, the next successful authentication resets `failed_login_count` to 0.

---

## 5. QR Code Token Cryptography & Lifecycle Specification

A QR code in VENUE NEXUS is an **identifier for a physical resource**, not a customer authentication credential or authorization grant. Possession of a QR code does not grant ordering or session authority.

1. **Generation:**
   - 128 bits of cryptographically secure randomness generated via `java.security.SecureRandom`.
   - Encoded as a 22-character URL-safe Base64 string without padding (`base64url`).
2. **Database Storage:**
   - Stored strictly as a 64-character hexadecimal SHA-256 hash in `resource_qr.token_hash`.
   - The plain token is returned to the manager **once** at generation time for rendering/printing and is never stored.
3. **URL Format [PROPOSED]:**
   - `${QR_BASE_URL}/q/{plainToken}` (e.g., `https://app.venuenexus.io/q/dGhpcy1pcy1hLXRlc3Q`)
4. **Rendering:**
   - Server-side generation using ZXing to produce high-resolution SVG and PNG formats.
5. **Lifecycle Operations:**
   - **Single Active QR Rule:** A resource can have at most one active QR code at any time (enforced by partial unique index `uq_resource_active_qr`).
   - **Revocation:** Marking status as `REVOKED` records `QR_REVOKED` in the audit log. A revoked QR stops resolving immediately with HTTP 410 `QR_REVOKED`.
   - **Reissue:** In a single database transaction, the existing active QR is marked `REVOKED` and a new `resource_qr` record is created.

---

## 6. Audit Trail & Hash-Chain Cryptographic Specification

The platform audit log is append-only, tamper-evident, and partitioned per tenant.

### 6.1 Audit Chain Scope & Database-Level Sequence Serialization
- **Per-Tenant Chain:** Every tenant maintains an isolated sequence starting at 1.
- **Platform Chain [PROPOSED]:** A dedicated platform sequence starting at 1 for `tenant_id IS NULL` system events, using a fixed all-zero UUID sentinel (`00000000-0000-0000-0000-000000000000`) in the database counter table.
- **Database Counter Table (`audit_chain`):** Maintains the sequence counter (`last_sequence`) and cryptographic tip (`last_hash`) per tenant directly in PostgreSQL.
- **Concurrency Serialization & Single Transaction Guarantee:** Writes to each tenant's chain are serialized at the database level by acquiring an exclusive row lock on the tenant's row in the `audit_chain` counter table (`SELECT ... FOR UPDATE` in a single transaction). The complete sequence of steps:
  1. Ensure the counter row exists: `INSERT INTO audit_chain (chain_key, last_sequence, last_hash) VALUES (?, 0, '000...000') ON CONFLICT (chain_key) DO NOTHING;`
  2. Lock the chain counter row: `SELECT last_sequence, last_hash FROM audit_chain WHERE chain_key = ? FOR UPDATE;`
  3. Increment sequence: `nextSequence = last_sequence + 1;`
  4. Compute SHA-256 hash using `last_hash` as `prev_hash`;
  5. Insert the new audit record into `audit_log`;
  6. Update the counter row: `UPDATE audit_chain SET last_sequence = ?, last_hash = ? WHERE chain_key = ?;`
  Simultaneous callers for the same tenant wait in line at the row lock. This guarantees gapless, strictly monotonic sequence numbers (1, 2, 3...) without duplicates, and prevents hash chain forks. A rollback undoes the counter advancement cleanly without leaving gaps.
- **Backstop Unique Index:**
  `CREATE UNIQUE INDEX uq_audit_log_tenant_seq ON audit_log (COALESCE(tenant_id, '00000000-0000-0000-0000-000000000000'::uuid), sequence);`
  Guarantees at the database engine level that no duplicate sequence number can ever be committed for any tenant or for the platform chain.
- **Prohibited Anti-Pattern:** A PostgreSQL `SEQUENCE` object must **not** be used (causes unrecoverable sequence gaps on rollbacks and does not isolate sequences per tenant).

### 6.2 Deterministic SHA-256 Hash Calculation
The hash of each row is computed by hashing the UTF-8 bytes of a canonical pipe-separated string:

```text
canonicalString = sequence + "|" +
                  tenantIdString + "|" +
                  eventType + "|" +
                  outcome + "|" +
                  userRefString + "|" +
                  traceId + "|" +
                  ipHashString + "|" +
                  sanitizedDetailsJson + "|" +
                  occurredAtIsoUtc + "|" +
                  prevHash
```

- **Genesis Block:** For `sequence = 1`, `prevHash` is 64 hexadecimal zeros:
  `0000000000000000000000000000000000000000000000000000000000000000`
- **Field Normalization:**
  - `tenantIdString`: standard 36-character UUID string, or `"null"` for platform chain.
  - `userRefString`: user ID string, or `"null"`.
  - `ipHashString`: SHA-256 hash of client IP, or `"null"`.
  - `occurredAtIsoUtc`: ISO-8601 UTC string (e.g. `2026-11-06T10:15:30.000Z`).
  - `sanitizedDetailsJson`: canonicalized JSON text with sensitive keys stripped.

### 6.3 Audit Event Types Catalogue

| Audit Event Type | Scope | Trigger Event | Status in Sprint 1 |
|---|---|---|---|
| `AUTH_LOGIN` | Staff | Successful staff login | **ACTIVE** |
| `QR_REVOKED` | Resource | QR identity revoked by manager | **ACTIVE** |
| `AUTH_LOGIN_FAILED` | Staff | Failed authentication attempt | **PROPOSED ACTIVE** |
| `AUTH_LOGOUT` | Staff | Staff explicit logout | **PROPOSED ACTIVE** |
| `AUTH_REFRESH` | Staff | Refresh token rotated | **PROPOSED ACTIVE** |
| `AUTH_REFRESH_REUSE_DETECTED`| Security | Revoked refresh token used | **PROPOSED ACTIVE** |
| `USER_CREATED` | Staff | New staff user created | **PROPOSED ACTIVE** |
| `TENANT_CREATED` | Platform | New tenant provisioned | **PROPOSED ACTIVE** |
| `QR_ISSUED` | Resource | QR identity issued for resource | **PROPOSED ACTIVE** |
| `QR_REISSUED` | Resource | QR identity revoked & reissued | **PROPOSED ACTIVE** |
| `PAYMENT_SUCCEEDED` [CR-02] | Payment | Payment captured | *RESERVED (Sprint 3)* |
| `REFUND_REQUESTED` [CR-02] | Payment | Refund requested | *RESERVED (Sprint 3)* |
| `SESSION_ENDED` [CR-04/05] | Session | Customer session closed | *RESERVED (Sprint 2)* |
| `AI_FEATURE_USED` [CR-03] | AI | AI recommendation invoked | *RESERVED (Sprint 2/3)* |

---

## 7. Data Sanitization & Secret Logging Prevention

1. **Audit Details Sanitization:**
   - The `AuditDetailsSanitizer` scans all metadata before recording.
   - Any key matching `password`, `token`, `secret`, `authorization`, `qr`, `otp`, `apiKey`, `creditCard`, or `cvv` (case-insensitive) is completely stripped or redacted to `[REDACTED]`.
2. **IP Address Hashing:**
   - Raw IP addresses are never logged or stored in audit tables; only their SHA-256 hash (`ip_hash`) is preserved.
3. **Log Sanitization:**
   - Logback MDC prints only `traceId`.
   - Exception handlers log unexpected exceptions server-side with `traceId` only, preventing sensitive request bodies from leaking into stdout/stderr.

---

## 8. AI Governance, Feature Flags & Data Handling [CR-03]

As specified in CR-03, AI capabilities (menu recommendations, customer chatbot, routing suggestions) are planned for Sprints 2 and 3:
1. **Advisory Only:** AI output is strictly advisory. It must never make autonomous decisions regarding access control, tenant boundaries, prices, billing, payments, or order status.
2. **Feature Flags:**
   - Controlled via boolean flags configured per tenant/location:
     - `FEATURE_FLAG_AI_RECOMMENDATIONS_ENABLED` (Default: `false`)
     - `FEATURE_FLAG_AI_CHATBOT_ENABLED` (Default: `false`)
     - `FEATURE_FLAG_AI_ROUTING_ENABLED` (Default: `false`)
3. **Data Protection Boundary:**
   - QR tokens, OTP values, passwords, customer phone numbers/emails, device fingerprints, JWTs, and payment credentials **must never be transmitted to an AI provider or written to AI logs**.
4. **Architectural Isolation:**
   - The AI module must interact with business modules strictly via public Java service interfaces and must not directly query foreign module repositories.

---

## 9. Reserved Configuration Names (CR-02, CR-03, CR-05)

The following configuration property names are reserved for later sprints:
- `PAYMENT_PROVIDER_NAME`, `PAYMENT_PROVIDER_API_KEY`, `PAYMENT_PROVIDER_WEBHOOK_SECRET`, `PAYMENT_MODE_DEFAULT`
- `AI_PROVIDER_NAME`, `AI_PROVIDER_API_KEY`
- `AUTO_LOGOUT_TRIGGER` (values: `TRANSACTION_COMPLETE` [proposed default, D-4] or `ORDER_PLACED`)
All values must be loaded from container environment variables and never committed to code.

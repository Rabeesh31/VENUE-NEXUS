# VENUE NEXUS — Security Architecture & Specifications

## 1. Public Security API for Module Authors

This section provides the contract and usage guidelines for authors of downstream modules (e.g., Phase 4 Staff/Hierarchy, Phase 5 Resource/QR).

### 1.1 `TenantContext`
- **Location:** `com.venuenexus.security.context.TenantContext`
- **Purpose:** ThreadLocal storage of the validated tenant ID for the current request.
- **Rule:** **NEVER accept `tenantId` from request payloads, query params, or path variables.** Always obtain it via `TenantContext.requireTenantId()` or `TenantContext.currentTenantId()`.
- **Exceptions:** Unauthenticated calls or `PLATFORM_ADMIN` requests return an empty `Optional<UUID>`. Calling `requireTenantId()` in those scenarios throws `ApiException(ErrorCode.ACCESS_DENIED)`.

### 1.2 `ScopeContext`
- **Location:** `com.venuenexus.security.context.ScopeContext`
- **Purpose:** Carries role and operational boundaries (`organisationId`, `locationId`, `departmentId`).
- **Methods:**
  - `isPlatformScoped()`: true if user is `PLATFORM_ADMIN`.
  - `isTenantLevel()`: true if user has tenant-wide access (no org/location constraints).
  - `isOrganisationScoped()`: true if restricted to an organisation.
  - `isLocationScoped()`: true if restricted to a specific physical location.

### 1.3 `CurrentStaff`
- **Location:** `com.venuenexus.security.context.CurrentStaff`
- **Purpose:** Immutable identity snapshot of the caller (`userId`, `role`, `tenantId`, `organisationId`, `locationId`, `departmentId`, `tokenId`).
- **Usage:** Used by modules needing the actor's identity (e.g. Phase 5 QR generation tracking the issuing staff `userId`).

### 1.4 `ScopeEnforcer` & `TenantScopedGuard`
- **Location:** `com.venuenexus.security.authz.ScopeEnforcer`, `com.venuenexus.security.authz.TenantScopedGuard`
- **Purpose:** Enforces tenant isolation and location/organisation clearance on tenant-owned records.
- **Usage:** Modules must first verify tenant ownership and then scope clearance:
  ```java
  TenantScopedGuard.assertSameTenant(record.getTenantId());
  scopeEnforcer.assertCanAccess(record.getOrganisationId(), record.getLocationId());
  ```
  If access is denied, it throws an `ApiException(ErrorCode.NOT_FOUND)` (404), ensuring out-of-scope or cross-tenant records appear non-existent and reveal no metadata.

### 1.5 `Roles` & `SecurityExpressions`
- **Location:** `com.venuenexus.security.authz.Roles`, `com.venuenexus.security.authz.SecurityExpressions`
- **Purpose:** Reusable `@PreAuthorize` expressions:
  - `SecurityExpressions.PLATFORM_ONLY`: `hasRole('PLATFORM_ADMIN')`
  - `SecurityExpressions.TENANT_ADMIN_ONLY`: `hasRole('TENANT_ADMIN')`
  - `SecurityExpressions.MANAGER_AND_ABOVE`: `hasAnyRole('TENANT_ADMIN','VENUE_MANAGER')`
  - `SecurityExpressions.SUPERVISOR_AND_ABOVE`: `hasAnyRole('TENANT_ADMIN','VENUE_MANAGER','SUPERVISOR')`
  - `SecurityExpressions.ANY_STAFF_ROLE`: `hasAnyRole('TENANT_ADMIN','VENUE_MANAGER','SUPERVISOR','STAFF')`

### 1.6 `PasswordEncoder`
- **Location:** Configured in `com.venuenexus.security.SecurityConfig`
- **Purpose:** Shared `BCryptPasswordEncoder` bean configured with standard bcrypt strength (12, range 4-31). Downstream modules (Phase 4 staff user creation) must inject this bean to hash passwords.

---

## 2. Token Architecture & Claims

### 2.1 Staff Access JWT
- **Format:** Signed JSON Web Token (JWT).
- **Algorithm Pinning:** Strictly `HS256` (HMAC with SHA-256). Any token signed with `none` or other algorithms is immediately rejected.
- **Secret Key:** Injected via `JWT_STAFF_SECRET` environment variable; MUST be at least 32 bytes (256 bits). Fails fast on startup if missing or too short.
- **Lifetime:** `15 minutes` (PT15M), configurable via `security.jwt.access-ttl`.
- **Claims:**
  - `sub`: `UUID` (User ID)
  - `jti`: `UUID` string (Unique token identifier for Redis denylisting)
  - `iss`: `venue-nexus`
  - `aud`: `venue-nexus-staff`
  - `typ`: `STAFF` (Must equal STAFF)
  - `rol`: `StaffRole` (`PLATFORM_ADMIN`, `TENANT_ADMIN`, `VENUE_MANAGER`, `SUPERVISOR`, `STAFF`)
  - `tid`: `UUID` (Tenant ID, absent for PLATFORM_ADMIN)
  - `oid`: `UUID` (Organisation ID, optional)
  - `lid`: `UUID` (Location ID, optional)
  - `did`: `UUID` (Department ID, optional)
  - `iat`: Timestamp (Issued at)
  - `exp`: Timestamp (Expires at)

### 2.2 Refresh Token
- **Format:** Cryptographically secure 256-bit random URL-safe Base64 opaque string (no embedded claims).
- **Storage:** Plaintext is NEVER persisted. Only its SHA-256 hex digest is stored in `refresh_token.token_hash`.
- **Lifetime:** `7 days` (P7D), configurable via `security.refresh.ttl`.
- **Family Rotation & Reuse Detection:**
  - On every rotation, the presented token is revoked and linked to the new token via `replaced_by_token_hash`.
  - If a revoked token is ever presented again, the **entire token family** is immediately revoked across all devices, an `AUTH_REFRESH_REUSE_DETECTED` audit event is written, and the request is rejected with HTTP 401. Revocation is committed in a new transaction (`Propagation.REQUIRES_NEW`) to survive the thrown `ApiException`.

---

## 3. Account Lockout & Protection

- **Failed Login Threshold:** 5 consecutive failed attempts (configurable via `security.login.max-failed-attempts`).
- **Lockout Duration:** 15 minutes (PT15M, configurable via `security.login.lockout-duration`).
- **Audit:** Lockout records `AUTH_LOCKOUT` audit event.
- **Lock Expiration:** Automatic after the lockout window based on injected `Clock`.
- **Deterministic Time:** All operations inject `java.time.Clock` rather than system time.
- **Admin Unlock:** `AccountUnlockService.unlock(userId)` allows `TENANT_ADMIN` (within tenant) or `PLATFORM_ADMIN` to immediately clear counters and unlock the account. Cross-tenant unlock attempts return HTTP 404 `NOT_FOUND`.

---

## 4. Token Denylist & Redis Fail-Closed Policy

- **Denylist Key:** `auth:denylist:jti:<jti>`
- **TTL:** Remaining lifetime of the revoked access token.
- **Outage Policy (D-P3-8):** Fail **closed**. If Redis is unreachable during token validation, requests fail closed (`ApiException(ErrorCode.AUTH_TOKEN_REVOKED)`) to prevent revoked tokens from being honored during infrastructure disruptions.

---

## 5. Log Hygiene & Audit Specifications

- **Zero Secret Leakage:** Log streams and audit payloads NEVER contain:
  - Plaintext passwords
  - Password hashes (`$2a$`, `$2b$`)
  - Raw access tokens
  - Raw refresh tokens
  - JWT signing secrets
  - Unmasked customer/staff emails
- **Traceability:** Every error, filter response, and audit record carries `traceId` matching the `X-Trace-Id` HTTP header.
- **Audit Events:**
  - `AUTH_LOGIN`: Successful authentication
  - `AUTH_LOGIN_FAILED`: Known user password failure
  - `AUTH_LOCKOUT`: Account locked out after threshold
  - `AUTH_REFRESH_REUSE_DETECTED`: Replayed refresh token detected; family revoked
  - `AUTH_TOKEN_REJECTED`: Rejected token with identifiable tenant
  - `ACCESS_DENIED_SCOPE`: Unauthorized scope clearance attempt
- **Micrometer Metrics:**
  - `auth.login{outcome}`: Tracks login attempts (`success`, `invalid_credentials`, `unknown_email`, `account_locked`)
  - `auth.lockout`: Tracks lockout events triggered
  - `auth.refresh{outcome}`: Tracks refresh rotation attempts (`success`, `invalid`, `reuse_detected`, `expired`)
  - `auth.token.rejected{reason}`: Tracks JWT rejections by reason code

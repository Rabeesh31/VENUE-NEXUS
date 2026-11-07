# 02 — Phase 3 Task Breakdown

Tasks are ordered for **integration safety**, not just for the allocation's step numbers. The allocation steps (3.1–3.5) are mapped on each task.

**Branching:** `feature/phase3-<short-name>` → PR into `develop`. After every merge, re-run the integrated build and tests (Plan → Build → Test → Integrate → Test again → Review/Merge).

## Order at a glance

```
P3-00 Pre-flight ──► P3-01 Public security contracts  (PR #1 — unblocks Phases 4, 5, 6)
                          │
        ┌─────────────────┼──────────────────┐
        ▼                 ▼                  ▼
  P3-02 AppUser      P3-04 JwtService    P3-09 SecurityConfig skeleton
  lookup/verify           │                  │
        │                 ▼                  ▼
  P3-03 Lockout     P3-08 StaffJwtFilter + handlers
        │                 │
        └──► P3-05 Login ◄┘
                 │
          P3-06 Refresh rotation ──► P3-07 Logout + denylist
                 │
          P3-10 @PreAuthorize + ScopeEnforcer + deny-by-default
                 │
          P3-11 Audit/metrics/log hygiene ──► P3-12 Tests + Postman ──► P3-14 Docs
          (P3-13 optional /auth/me)
```

---

## P3-00 · Pre-flight (no code)

**Goal:** Prove every upstream dependency exists before writing code.

1. Confirm the repo builds: `mvn -q verify` (backend).
2. Locate and read (see file 06 §A): `ErrorCode`, `ApiException`, `ApiError`, `GlobalExceptionHandler`, `TraceIdFilter`, `AuditLogService`, `AuditEvent`, `AuditEventType`, `docs/security.md`, `docs/api-contract.md`, `docs/db-schema.md`.
3. Confirm Flyway V2 exists and read the **actual** column names of `app_user` and `refresh_token`; locate the Phase 2 entities/repositories.
4. Confirm Docker Compose starts PostgreSQL and Redis; confirm `JWT_STAFF_SECRET` and `REDIS_*` exist in the env template.
5. Output a **gap report**: what is present, what is missing, what differs from this pack. **Stop and ask if anything marked `[HANDOFF]` is missing.**

**Done when:** gap report produced; no assumption left unverified silently.

---

## P3-01 · Public security contracts (PR #1 — do this first)  *(supports 3.4 / 3.5)*

**Why first:** Phase 4 (Ashwin) and Phase 5 (Monisha) need these types to compile and to write scoped services. Shipping them early removes the biggest integration blocker.

Create (names are the proposal; align with `security.md` if it differs):

```java
// security.context
public final class TenantContext {                       // ThreadLocal holder
    public static Optional<UUID> currentTenantId();
    public static UUID requireTenantId();                // throws ApiException(ACCESS_DENIED) when absent
    // set/clear are package-private or restricted to the filter
}

public record ScopeContext(StaffRole role, UUID tenantId,
                           UUID organisationId, UUID locationId, UUID departmentId) {
    public static Optional<ScopeContext> current();
    public static ScopeContext require();
    public boolean isPlatformScoped();      // role == PLATFORM_ADMIN
    public boolean isTenantLevel();         // no org, no location
    public boolean isOrganisationScoped();  // org set, location null
    public boolean isLocationScoped();      // location set
}

public record CurrentStaff(UUID userId, StaffRole role, UUID tenantId,
                           UUID organisationId, UUID locationId, UUID departmentId, String tokenId) {
    public static CurrentStaff require();
}

// security.authz
public final class Roles { /* String constants matching the frozen role enum */ }
public final class SecurityExpressions { /* reusable @PreAuthorize strings, see file 03 §9 */ }

public interface ScopeEnforcer {
    /** Caller already loaded the record tenant-scoped; pass the record's own org/location ids. */
    void assertCanAccess(UUID recordOrganisationId, UUID recordLocationId);
    boolean canAccess(UUID recordOrganisationId, UUID recordLocationId);
    /** For list endpoints: the location ids the caller is restricted to, or empty = unrestricted within tenant. */
    Optional<UUID> restrictedLocationId();
    Optional<UUID> restrictedOrganisationId();
}

// config
@Bean PasswordEncoder passwordEncoder();   // BCrypt, strength from config — SHARED with Phase 4 (staff creation)
```

Rules:
- `StaffRole` must be the **frozen role enum from `security.md`/Phase 1**. If it does not exist yet, create nothing — stop and ask (D-P3-9).
- Provide a **no-op/throwing stub implementation** of `ScopeEnforcer` so dependants compile; real logic arrives in P3-10.
- Write the Javadoc contract for each method (what it throws, thread-safety, "never accept tenant from client").
- Add `docs/security.md` section "Public security API for module authors".

**Acceptance:** compiles; ArchUnit passes; Phase 4/5 can inject/call these without waiting for login.

---

## P3-02 · AppUser lookup, status validation, password verification  *(Step 3.1)*

1. `AuthenticationLookup` (identity service) to load a user **by email, case-insensitively**, using the Phase 2 repository.
   - Pre-authentication the tenant is unknown, so this is the **single sanctioned exception** to "tenant-scoped repository" — it must be an explicit, named method (e.g. `findForAuthenticationByEmail`) agreed with Phase 2 and allow-listed in the ArchUnit rule `[DECISION D-P3-10]`.
2. Normalise input: trim, lower-case; reject blank → `VALIDATION_ERROR`.
3. Account status validation using the **actual V2 status values** (do not invent). Disabled/inactive account → generic invalid-credentials failure `[ASSUMPTION]` (confirm).
4. Tenant status: if the user's tenant is `SUSPENDED`, login must fail `[ASSUMPTION — recommended; confirm]`. PLATFORM_ADMIN has no tenant (exception).
5. `PasswordVerifier`: BCrypt `matches`. **Timing equalisation:** when the email is unknown, still run a BCrypt comparison against a pre-computed dummy hash of the same strength, so response time does not reveal whether the email exists.
6. Unknown email, wrong password → **identical** `AUTH_INVALID_CREDENTIALS` response (same message, same status, same shape).

**Acceptance:** unit tests prove identical outcome for unknown email vs wrong password; dummy-hash path exercised; no password or hash in logs/exceptions.

---

## P3-03 · Failed-login counting, lockout, unlock  *(Step 3.1, FR-ID-07)*

1. On wrong password for a **known** user: increment failed-login count (V2 column).
2. When count reaches `maxFailedAttempts` (default **5**, config `[ASSUMPTION]`): set the lock timestamp; user is locked for `lockoutDuration` (default **15 min**, config `[ASSUMPTION]`).
3. A locked user → `AUTH_ACCOUNT_LOCKED` **before** password verification. The UI needs a distinct "account locked" state (UI/UX §7.1).
4. Lock expires automatically at the end of the duration; the next successful login resets the counter and clears the lock.
5. Success resets failed count and updates last-login timestamp.
6. **Admin unlock:** provide `AccountUnlockService.unlock(userId)` (clears count + lock) with `@PreAuthorize` for TENANT_ADMIN/PLATFORM_ADMIN within scope, tenant-scoped lookup. **Ownership of the HTTP endpoint** (Phase 3 vs Ashwin's 4.3) is `[DECISION D-P3-6]` — build the service now; expose a route only after the owner is confirmed.
7. Audit: `AUTH_LOCKOUT` when a lock is applied `[HANDOFF names AUTH_LOGIN, AUTH_LOGIN_FAILED; AUTH_LOCKOUT is in the PRD event list — verify it exists in AuditEventType]`.
8. Updates must be race-safe (atomic increment or optimistic locking) so parallel bad logins cannot bypass the threshold.

**Acceptance:** 5 wrong attempts → locked; correct password during lock still rejected; after expiry login works; counter resets on success; unlock works; concurrent-attempts test passes.

---

## P3-04 · Staff JWT service  *(Step 3.2 prerequisite)*

`StaffJwtService`:
- `issue(CurrentStaff-like input) → AccessToken(token, jti, expiresAt)`.
- `parseAndValidate(String) → StaffTokenClaims` which throws `ApiException` with the **precise** code:

| Failure | ErrorCode `[HANDOFF]` |
|---|---|
| Malformed, bad signature, `alg=none`, unexpected algorithm, missing required claim | `AUTH_TOKEN_INVALID` |
| `exp` in the past | `AUTH_TOKEN_EXPIRED` |
| `typ` ≠ `STAFF` | `AUTH_TOKEN_WRONG_TYPE` |
| `aud` mismatch (also `iss` mismatch) | `AUTH_TOKEN_INVALID_AUDIENCE` |
| `jti` on denylist (checked in filter) | `AUTH_TOKEN_REVOKED` |

Claims `[HANDOFF — frozen in security.md]`: `uid, typ, rol, tid, oid, lid, did, jti, aud, iss` plus `iat`, `exp`. Details in file 03 §2.

Rules:
- Algorithm pinned (default HS256); **reject every other algorithm**, including `none`.
- Key from `JWT_STAFF_SECRET`; **fail application start** if missing or < 32 bytes `[FROZEN: secrets ≥ 32 random bytes]`.
- Tokens carry **no** prices, business data or personal data (no email, no name).
- Optional claims (`tid` for PLATFORM_ADMIN, `oid`, `lid`, `did`) are **omitted**, not `null`.
- Small clock-skew leeway is `[ASSUMPTION]`: default 0–30 s, configurable; tests must not depend on it.
- Token signed with the *session* key or any other key must fail (future-proofing; add a test using a different secret).

**Acceptance:** round-trip test; expired, tampered, wrong-type, wrong-audience, `alg=none`, wrong-key tokens each map to the exact code above.

---

## P3-05 · `POST /api/v1/auth/login`  *(Step 3.2)*

Flow (full spec in file 03 §3):
1. Validate body (`email`, `password`) → `VALIDATION_ERROR` on blank.
2. P3-02 lookup + status checks → P3-03 lockout check → verify password.
3. On failure: increment counter (known user), audit `AUTH_LOGIN_FAILED`, return `AUTH_INVALID_CREDENTIALS`.
4. On success: reset counter, update last login, issue access JWT (P3-04) and **opaque refresh token**: 32 bytes from `SecureRandom`, base64url; persist **only** `SHA-256(token)` with a **new family id**, `expires_at = now + 7d`.
5. Audit `AUTH_LOGIN` via `AuditLogService.record(AuditEvent.success(tenantId, userId, AuditEventType.AUTH_LOGIN, …))` `[HANDOFF]`.
6. Response: tokens + user summary (shape in file 03 §3 — **must be reconciled with `api-contract.md` and Phase 6** before merge).
7. Endpoint is **public** (permitAll); everything else requires authentication.

**Acceptance:** valid login → 200 with tokens; plaintext refresh token never persisted or logged; `X-Trace-Id` header present; error body contains `traceId`; DB row contains hash only.

---

## P3-06 · Refresh rotation and reuse detection  *(Step 3.3)*

`POST /api/v1/auth/refresh` (public; body carries the refresh token) — see file 03 §4.

1. Hash the presented token; look up by hash.
2. Not found → `AUTH_TOKEN_INVALID`.
3. Expired → `AUTH_TOKEN_EXPIRED`.
4. **Already used/revoked (reuse)** → revoke **the entire family**, audit `AUTH_REFRESH_REUSE_DETECTED`, return `AUTH_REFRESH_REUSE_DETECTED`.
5. Otherwise, in **one transaction**: mark the old row revoked + set replacement reference; insert the new row in the **same family**; issue a new access token. Re-read the user from the DB and re-check status/lock/tenant status; role/scope in the new token come from the DB, not the old token.
6. **Critical transaction rule:** the family-revocation write in step 4 must **commit even though an exception is thrown** (use `noRollbackFor` or a `REQUIRES_NEW` sub-transaction). Add a test that fails if it rolls back.
7. Lock the token row (`SELECT … FOR UPDATE` / pessimistic write) so two simultaneous refreshes cannot both succeed.

**Acceptance:** rotation issues a new pair; old token rejected; reusing an old token kills the family (the newest token also stops working); concurrent refresh test: exactly one success.

---

## P3-07 · Logout and token denylist  *(Step 3.3)*

`POST /api/v1/auth/logout` (authenticated) — see file 03 §5.

1. Revoke the refresh-token **family** of the supplied refresh token (must belong to the caller).
2. Add the **current access token's `jti`** to the Redis denylist with TTL = remaining token lifetime (key in file 03 §7).
3. Return `204`. Idempotent: logging out twice is not an error.
4. `StaffJwtFilter` consults the denylist on every request → `AUTH_TOKEN_REVOKED`.
5. **Redis unavailable:** fail **closed** — reject authenticated requests rather than skip the check `[DECISION D-P3-8; PRD states fail-closed for Redis-backed controls]`; increment a metric; never log the token.

**Acceptance:** after logout, the same access token → 401 `AUTH_TOKEN_REVOKED`; the refresh token no longer works; Redis outage test passes (fail-closed).

---

## P3-08 · `StaffJwtFilter`, contexts, JSON error handlers  *(Step 3.4)*

1. `StaffJwtFilter extends OncePerRequestFilter`:
   - Read `Authorization: Bearer …`; if absent, continue unauthenticated (SecurityConfig decides).
   - Validate via P3-04, then denylist check.
   - Populate Spring `SecurityContext` (authorities `ROLE_<rol>`), `TenantContext`, `ScopeContext`.
   - `finally` block: **always** clear `SecurityContextHolder`, `TenantContext`, `ScopeContext`.
2. **Filters run before MVC**, so `GlobalExceptionHandler` will **not** see exceptions thrown here. Implement:
   - `AuthenticationEntryPoint` → writes the standard `ApiError` JSON (401).
   - `AccessDeniedHandler` → writes the standard `ApiError` JSON (403).
   - Both include `traceId` via `TraceIdFilter.getCurrentTraceId()` `[HANDOFF]` and set `Content-Type: application/json`.
   - Use Phase 1's `ApiError` builder/serializer so field names are **identical** to MVC-produced errors (`timestamp, status, error, message, path, traceId`).
3. The filter must **not** be registered twice: do not make it a plain `@Component` servlet filter without a `FilterRegistrationBean` that disables auto-registration.
4. `TraceIdFilter` already runs at `Ordered.HIGHEST_PRECEDENCE` `[HANDOFF]`; the JWT filter must run **after** it so rejected requests still carry a trace ID.
5. Rejections: increment `auth.token.rejected{reason}` metric and log at WARN with `traceId` + reason code — **never** the token. Audit `AUTH_TOKEN_REJECTED` only when a tenant can be determined `[DECISION D-P3-7]`.

**Acceptance:** 401 body for a bad token is byte-for-byte the same shape as an MVC error; contexts empty after each request (test with two sequential requests on one thread); header `X-Trace-Id` present on 401/403 responses.

---

## P3-09 · `SecurityConfig`  *(Step 3.4)*

Spec in file 03 §8. Summary:
- Stateless; CSRF disabled (bearer API); form login/basic/default logout disabled.
- `permitAll`: `/api/v1/auth/login`, `/api/v1/auth/refresh`, health endpoint; OpenAPI/Swagger only in non-prod profiles `[ASSUMPTION]`.
- `/api/v1/**` → authenticated. **`anyRequest().denyAll()`** as the final rule (deny-by-default).
- `@EnableMethodSecurity`.
- CORS from `CORS_ALLOWED_ORIGINS`; **expose `X-Trace-Id`** to the browser; allow `Authorization`, `Content-Type`, `Idempotency-Key`.
- Register `bearerAuth` in OpenAPI so the frontend's generated client sends the header (Phase 6 dependency).
- Do not enable `/api/v1/public/**` or `/api/v1/portal/**` (Sprint 2) — they stay denied.

**Acceptance:** unknown path → denied; unauthenticated call to protected route → 401 JSON; preflight CORS OK for the configured origin and exposes `X-Trace-Id`.

---

## P3-10 · `@PreAuthorize`, scope enforcement, deny-by-default  *(Step 3.5)*

1. Implement `ScopeEnforcer` (replace the P3-01 stub):
   - `PLATFORM_ADMIN` is platform-scoped: allowed on platform operations only; **no raw tenant business data** unless policy allows `[FROZEN PRD §2.1]`.
   - Tenant-level user: any record in own tenant (the caller's repository call is already tenant-scoped).
   - Organisation-scoped user: record's `organisationId` must equal the claim `oid`.
   - Location-scoped user: record's `locationId` must equal the claim `lid`.
   - Out-of-scope → **`NOT_FOUND`-style result so the existence of the record is not revealed** `[FROZEN PRD §12.1: cross-tenant and cross-location → 404]`. Whether `LOCATION_SCOPE_VIOLATION` is used for *explicit* location parameters is `[DECISION D-P3-2]`.
2. `Roles` / `SecurityExpressions` constants so the (still unapproved) role matrix is changed **in one place** `[DECISION D-P3-9]`.
3. Provide ready-to-use method guards for Phase 4/5 controllers/services (file 03 §9) and document usage with examples.
4. Audit `ACCESS_DENIED_ROLE` / `ACCESS_DENIED_SCOPE` / `ACCESS_DENIED_TENANT` where Phase 1's `AuditEventType` provides them.
5. Provide `TenantScopedGuard` helper or documented pattern: **never** call an unscoped repository method.

**Acceptance:** with test-only controllers (file 05 §3): wrong role → 403; Tenant A → Tenant B → 404; location-scoped user → other location → 404; every route without an explicit rule → denied.

---

## P3-11 · Audit, metrics, log hygiene  *(cross-cutting)*

- Audit via the injected `AuditLogService` only (never write the table directly): `AUTH_LOGIN`, `AUTH_LOGIN_FAILED`, `AUTH_LOCKOUT`, `AUTH_REFRESH_REUSE_DETECTED`, `AUTH_TOKEN_REJECTED` (conditional), `ACCESS_DENIED_*`.
- Audit **detail must contain no secrets or unnecessary personal data**: no password, token, hash, or full email (use user id; if an identifier is unavoidable use a masked/hashed form).
- Hash IP if recorded `[FROZEN TRD §6.6: hashed IP address]`.
- Micrometer counters: `auth.login{outcome}`, `auth.lockout`, `auth.refresh{outcome}`, `auth.token.rejected{reason}`.
- Logs: structured, `traceId` + `userId`; **never** password, hash, access/refresh token, signing key.

---

## P3-12 · Tests, fixtures, Postman

Full plan in file 05. Minimum: unit + Testcontainers integration + MockMvc API + security tests, a log-capture test proving no secrets, and a Postman collection for Rabeesh (Phase 8).

---

## P3-13 · (Optional, needs contract approval) `GET /api/v1/auth/me`

Returns the caller's profile and scope. Useful for page reloads on the frontend. **Do not build unless Rithik/Lackshana add it to `api-contract.md`.** The login response already contains the user summary.

---

## P3-14 · Documentation in the same PR

Update `docs/security.md` (token claims, lifetimes, denylist keys, lockout policy, public security API) and `docs/api-contract.md` (auth endpoints, error codes used) — and keep OpenAPI annotations in sync.

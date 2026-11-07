# 04 — Dependencies and Integration

Goal: **zero surprises at merge time.** Every cross-phase touchpoint is listed with its owner, the artefact exchanged and how to verify it.

## 1. Upstream — what Phase 3 needs from others

| # | From | Needs | Why | Status | Verify by |
|---|------|-------|-----|--------|-----------|
| U1 | Phase 1 · Rithik | `ErrorCode` enum (incl. AUTH_* codes) + HTTP mapping, `ApiException` | Throw contract-conformant errors | `[HANDOFF]` names known; **source needed** | Compile; unit test each code |
| U2 | Phase 1 | `ApiError` model + `GlobalExceptionHandler` | Same JSON from MVC and filters | Source needed | Compare JSON from a controller error vs filter error |
| U3 | Phase 1 | `TraceIdFilter` + `getCurrentTraceId()` (HIGHEST_PRECEDENCE) | `traceId` in 401/403 bodies | `[HANDOFF]` known | `X-Trace-Id` header on every response |
| U4 | Phase 1 | `AuditLogService`, `AuditEvent.success/failure…`, `AuditEventType` (`AUTH_LOGIN`, `AUTH_LOGIN_FAILED`, others) | Audit | `[HANDOFF]` partial; **source needed** | Audit chain verifies after login |
| U5 | Phase 1 | `docs/security.md` frozen: claims, `aud`/`iss`, algorithm, lockout policy, role enum/matrix | Contract | Needed | Diff against file 03 |
| U6 | Phase 1 | `docs/api-contract.md` auth section + error table | Request/response shapes | Needed | Diff against file 03 §3–6 |
| U7 | Phase 1 | `pom.xml` (security, JWT lib, data-redis, test deps), env template, Docker Compose with Redis | Build and run | Needed | `mvn verify`, `docker compose up` |
| U8 | Phase 1 | Base package name, module layout, ArchUnit rules | Place code correctly | Needed | ArchUnit passes |
| U9 | Phase 2 · Kavisharmitha | Flyway **V2** (`app_user`, `refresh_token`), entities, repositories, enums (`Role`, user status) | Persistence | Needed | Migration on empty DB; app starts |
| U10 | Phase 2 | An **explicit, named** non-tenant-scoped lookup for pre-login (`app_user` by email, `refresh_token` by hash) and ArchUnit allow-list | Login happens before the tenant is known | **Decision D-P3-10** | ArchUnit rule has exactly these exceptions |
| U11 | Infra | Redis reachable in dev/test (Testcontainers for tests) | Denylist | Compose | Integration test |

## 2. Downstream — what others need from Phase 3

| # | To | Artefact | Needed by | Notes |
|---|----|----------|-----------|-------|
| D1 | **Ashwin (4)** | `TenantContext`, `ScopeContext`, `CurrentStaff`, `ScopeEnforcer`, `Roles`, `SecurityExpressions` | Day 1 (P3-01) | Hierarchy/staff APIs use them for scope and `@PreAuthorize` |
| D2 | **Ashwin (4)** | Shared `PasswordEncoder` bean (BCrypt) | Day 1 | Staff-user creation must hash with the **same** encoder/strength |
| D3 | **Ashwin (4)** | `AccountUnlockService.unlock(userId)` | When staff-user endpoints are built | Endpoint ownership = D-P3-6 |
| D4 | **Monisha (5)** | Same contexts + `CurrentStaff.userId()` | Day 1 | `resource_qr` stores the issuing staff user |
| D5 | **Monisha (5)** | `@PreAuthorize` guards for QR issue/revoke/reissue; `ScopeEnforcer` | Day 1–2 | QR revocation audit `QR_REVOKED` is theirs |
| D6 | **Lackshana (6)** | Final login/refresh/logout contract + OpenAPI (`bearerAuth`) | Before API-client generation | Response shape must match |
| D7 | **Lackshana (6)** | Error-code → UI-state mapping (below) | Login screen | Invalid / locked / expired / revoked / network |
| D8 | **Lackshana (6)** | CORS config exposing `X-Trace-Id` | Frontend dev | Needed to show trace IDs in the UI |
| D9 | **Yuvan (7)** | 403 vs 404 behaviour rules | Screens | Permission-denied vs not-found states |
| D10 | **Rabeesh (8)** | Postman collection, test evidence, security test results, **seed users** per role/scope + two tenants | WP9 / demo | See file 05 §6 |
| D11 | Everyone | `docs/security.md` + `docs/api-contract.md` updates in the same PRs | Each PR | Contract-first rule |

### 2.1 Error code → frontend behaviour (give to Lackshana)

| ErrorCode | UI behaviour |
|---|---|
| `AUTH_INVALID_CREDENTIALS` | Generic "Email or password is incorrect" (never say which) |
| `AUTH_ACCOUNT_LOCKED` | "Account locked" state; advise contacting an administrator / retry later |
| `AUTH_TOKEN_EXPIRED` | Try `/auth/refresh` **once**; on failure → login with "Session expired" |
| `AUTH_TOKEN_INVALID`, `…_WRONG_TYPE`, `…_INVALID_AUDIENCE`, `AUTH_TOKEN_REVOKED` | Clear cached data → login |
| `AUTH_REFRESH_REUSE_DETECTED` | Clear everything → login (security notice) |
| `ACCESS_DENIED` / 403 | Permission-denied state; no protected data |
| 404 | "Not found" (also what out-of-scope looks like) |

## 3. Integration order (continuous, not at the end)

| Step | What | Gate before next step |
|---|---|---|
| 0 | Phase 1 handoff files received and read (P3-00 gap report) | No `[HANDOFF]` item missing |
| 1 | **PR #1: public security contracts (P3-01)** merged to `develop` | Phases 4 & 5 compile against it |
| 2 | JWT service + filter + SecurityConfig + handlers (P3-04, 08, 09) with test-only protected controller | 401/403 JSON identical in shape to MVC errors |
| 3 | Login + lockout (P3-02, 03, 05) against real V2 | Rabeesh/Lackshana can log in from Postman/UI |
| 4 | Refresh + logout + denylist (P3-06, 07) | Reuse-detection and logout tests green |
| 5 | Scope enforcement (P3-10) wired into Ashwin's/Monisha's services | Cross-tenant / cross-location tests green on **real** endpoints |
| 6 | Audit/metrics/log hygiene (P3-11) | Audit chain verifies; log test green |
| 7 | Phase 8 integrated run: Postman + browser flow + security suite | Exit criteria in file 08 |

After **every** merge into `develop`: rebuild, run full tests, re-run the smoke script (§5).

## 4. Integration handshakes (one short checklist per partner)

**With Rithik (Phase 1)**
- [ ] I have `ErrorCode`, `ApiException`, `ApiError`, `AuditLogService`, `AuditEvent`, `AuditEventType`, `TraceIdFilter` source.
- [ ] `security.md` / `api-contract.md` read; differences recorded.
- [ ] A filter-level 401 and an MVC-level 400 produce the **same JSON field set**.
- [ ] Failed login for an **unknown email** (no tenant) — how is it audited? (D-P3-7)

**With Kavisharmitha (Phase 2)**
- [ ] V2 column names/enums read from the real SQL, not guessed.
- [ ] Named pre-auth lookups exist and are the only unscoped methods; ArchUnit allow-list updated.
- [ ] Refresh-token repository supports: find by hash (with row lock), revoke family, insert next in family, set replacement reference.
- [ ] Failed-count increment can be atomic (query or `@Version`).
- [ ] Email uniqueness is case-insensitive (matches my lower-casing).

**With Ashwin (Phase 4)**
- [ ] He uses my `PasswordEncoder` when creating staff.
- [ ] His services call `ScopeEnforcer`/tenant-scoped repositories; no `tenantId` in his DTOs.
- [ ] Unlock endpoint owner agreed (D-P3-6).
- [ ] He exposes whatever the location switcher needs (permitted locations) — not Phase 3's job, but agree it.

**With Monisha (Phase 5)**
- [ ] She takes the issuing user from `CurrentStaff`, role guards from `SecurityExpressions`.
- [ ] Cross-tenant resource lookup returns 404.

**With Lackshana (Phase 6)**
- [ ] Login/refresh/logout JSON agreed; OpenAPI shows `bearerAuth`.
- [ ] Refresh-token transport agreed (D-P3-3).
- [ ] `X-Trace-Id` visible to browser JS via CORS expose header.

**With Rabeesh (Phase 8)**
- [ ] Seed data: Tenant A and Tenant B; users for each role; one location-scoped user in each of two locations; one locked user.
- [ ] Postman collection delivered with environment file (no real secrets).

## 5. Smoke script (run after every merge)

```bash
BASE=http://localhost:8080/api/v1

# 1. Login
curl -s -i -X POST $BASE/auth/login -H 'Content-Type: application/json' \
  -d '{"email":"<seed-manager-email>","password":"<seed-password-from-env>"}'
# expect: 200, X-Trace-Id header, accessToken + refreshToken

# 2. Protected call without token  -> 401 JSON with traceId
curl -s -i $BASE/<any-protected-route>

# 3. Protected call with token     -> 200 (or 403 if role not allowed)
curl -s -i $BASE/<any-protected-route> -H "Authorization: Bearer $ACCESS"

# 4. Refresh -> new pair; re-send the OLD refresh token -> AUTH_REFRESH_REUSE_DETECTED
# 5. Logout  -> 204; same access token again -> 401 AUTH_TOKEN_REVOKED
```

## 6. Known integration failure modes — and the built-in prevention

| # | Failure mode | Prevention in this pack |
|---|--------------|--------------------------|
| 1 | Filter-level errors don't pass through `GlobalExceptionHandler` → different JSON shape | P3-08 JSON entry point/denied handler using Phase 1 `ApiError`; test compares shapes |
| 2 | `StaffJwtFilter` registered twice (servlet + security chain) | `FilterRegistrationBean` disabled / add only inside the security chain |
| 3 | Family revocation rolled back when the reuse exception is thrown → attack not contained | `noRollbackFor` / `REQUIRES_NEW`; dedicated test |
| 4 | Two concurrent refreshes both succeed | Pessimistic row lock; concurrent test |
| 5 | Pre-login lookup breaks "no unscoped repository" rule / ArchUnit | Single named exception agreed with Phase 2 (D-P3-10) |
| 6 | Role name mismatch (`ROLE_` prefix, `hasRole` vs `hasAuthority`) | One convention in `Roles`; tests per role |
| 7 | Phase 4 hashes passwords differently than login verifies | Shared `PasswordEncoder` bean + same strength |
| 8 | Contexts leak between requests on pooled threads | `finally` clear; sequential-request test |
| 9 | Frontend can't read `X-Trace-Id` (CORS) | `exposedHeaders` |
| 10 | Generated API client has no auth | `bearerAuth` in OpenAPI |
| 11 | Secrets/tokens in logs or audit | Log-capture test; code review checklist; secret scan in CI |
| 12 | Redis down silently disables denylist | Fail-closed + metric + test |
| 13 | Email case mismatch → user "not found" | Lower-case + case-insensitive index |
| 14 | UUID claims parsed inconsistently | Single `StaffTokenClaims` parser; tests |
| 15 | Clock skew flakiness in tests | Inject `Clock`; never `sleep` for expiry |
| 16 | Hibernate auto-DDL diverges from Flyway | `ddl-auto=none` / `validate`; app starts on migrated schema |
| 17 | Role matrix approved late → rework | All permissions in `Roles`/`SecurityExpressions` (one file) |
| 18 | Test-only controllers leak to prod | `src/test` only, or `@Profile("test")` |

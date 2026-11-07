# 05 — Test Plan (Phase 3)

Tooling `[FROZEN]`: JUnit 5, Mockito, Testcontainers (PostgreSQL + Redis), ArchUnit, Postman. Inject a `Clock`; never `Thread.sleep` for expiry.

## 1. Test layers

| Layer | Scope |
|---|---|
| Unit | Password verification, lockout logic, JWT issue/parse, refresh rotation logic, scope enforcement |
| Integration | Real PostgreSQL via Flyway V1–V4 + real Redis (Testcontainers): login persistence, refresh rows, denylist TTL |
| API (MockMvc/HTTP) | Status codes, error body shape, headers, role/tenant/location behaviour |
| Security | Cross-tenant, cross-location, role escalation, token attacks, reuse detection, replay |
| Architecture | ArchUnit: module boundaries, no controller→repository, only the sanctioned unscoped lookups |
| Hygiene | Log-capture: no secrets |

## 2. Test matrix

| ID | Area | Scenario | Expected |
|---|---|---|---|
| T-01 | Login | Valid credentials | 200; access JWT (15 min, `typ=STAFF`, correct claims); refresh token returned |
| T-02 | Login | Wrong password (known user) | 401 `AUTH_INVALID_CREDENTIALS`; failed count +1 |
| T-03 | Login | Unknown email | 401 `AUTH_INVALID_CREDENTIALS`; **response identical** to T-02 (status, message, body shape) |
| T-04 | Login | Timing equalisation | Unknown-email path performs a BCrypt comparison (assert the dummy-hash path is invoked) |
| T-05 | Login | Email case/whitespace (`  Manager@X.com `) | Logs in |
| T-06 | Login | Blank email/password | 400 `VALIDATION_ERROR` |
| T-07 | Lockout | N consecutive failures (N = config) | Account locked, `AUTH_ACCOUNT_LOCKED`, `AUTH_LOCKOUT` audited |
| T-08 | Lockout | Correct password while locked | Still rejected |
| T-09 | Lockout | After lock duration (fake `Clock`) | Login works; counter reset |
| T-10 | Lockout | Success resets counter | Count = 0, `last_login` updated |
| T-11 | Lockout | Concurrent wrong attempts | Threshold still respected (no bypass) |
| T-12 | Lockout | Admin unlock | Count/lock cleared; tenant-scoped; wrong-tenant admin → 404 |
| T-13 | Status | Disabled user / suspended tenant | Rejected generically |
| T-14 | Refresh store | DB after login | Only SHA-256 hash stored; plaintext absent; family id set; expiry = +7 d |
| T-20 | JWT | Expired | 401 `AUTH_TOKEN_EXPIRED` |
| T-21 | JWT | Tampered payload/signature | 401 `AUTH_TOKEN_INVALID` |
| T-22 | JWT | Wrong type (`typ` ≠ `STAFF`) | 401 `AUTH_TOKEN_WRONG_TYPE` |
| T-23 | JWT | Wrong audience / issuer | 401 `AUTH_TOKEN_INVALID_AUDIENCE` |
| T-24 | JWT | `alg=none` | 401 `AUTH_TOKEN_INVALID` |
| T-25 | JWT | Signed with a different key | 401 `AUTH_TOKEN_INVALID` |
| T-26 | JWT | Missing header / malformed `Bearer` | 401 (JSON, `traceId`) |
| T-27 | JWT | Denylisted `jti` | 401 `AUTH_TOKEN_REVOKED` |
| T-28 | JWT | Missing optional claims (platform admin) | Parsed; contexts reflect no tenant |
| T-30 | Refresh | Valid refresh | New access + **new** refresh; old row revoked with replacement ref; same family |
| T-31 | Refresh | Reuse of rotated token | `AUTH_REFRESH_REUSE_DETECTED`; **entire family revoked** (newest token also fails); audit written; **revocation survived the exception** |
| T-32 | Refresh | Expired refresh | `AUTH_TOKEN_EXPIRED` |
| T-33 | Refresh | Unknown/garbage token | `AUTH_TOKEN_INVALID` |
| T-34 | Refresh | Two parallel refreshes of same token | Exactly one succeeds |
| T-35 | Refresh | User role/scope changed since login | New access token reflects DB values |
| T-36 | Refresh | User disabled/locked meanwhile | Refused |
| T-40 | Logout | Logout with valid tokens | 204; family revoked |
| T-41 | Logout | Access token reused after logout | 401 `AUTH_TOKEN_REVOKED` |
| T-42 | Logout | Refresh token reused after logout | Rejected |
| T-43 | Logout | Twice | Idempotent (no error) |
| T-44 | Logout | Someone else's refresh token | Not revoked; safe error |
| T-45 | Denylist | TTL | Key expires at the token's remaining lifetime |
| T-46 | Denylist | Redis down | Fail-closed; metric incremented |
| T-50 | RBAC | Wrong role on guarded endpoint | 403 JSON with `traceId` |
| T-51 | RBAC | Each role × each guarded test endpoint | Matches the matrix |
| T-52 | RBAC | Unlisted route | Denied (deny-by-default) |
| T-53 | RBAC | Unauthenticated protected route | 401 |
| T-54 | Tenant | Tenant A token → Tenant B record id | **404**, no data, no hint |
| T-55 | Tenant | `tenantId=B` in body/query/path with Tenant A token | Ignored; A's data only |
| T-56 | Tenant | Pagination/lists | Never contain other-tenant rows |
| T-57 | Scope | Location-scoped user → other location in same tenant | **404** |
| T-58 | Scope | Org-scoped user → location of another org | 404 |
| T-59 | Scope | Tenant-level user → any location in tenant | Allowed |
| T-60 | Scope | Platform admin → tenant business data | Denied unless policy says otherwise |
| T-61 | Context | Two sequential requests on one thread | `TenantContext`/`ScopeContext`/`SecurityContext` empty between them |
| T-62 | Errors | Filter-level 401 vs MVC 400 | **Same JSON field set** and `traceId` = `X-Trace-Id` |
| T-63 | CORS | Preflight from allowed origin | OK; `X-Trace-Id` exposed |
| T-70 | Audit | Login success / failure / lockout / reuse | Events recorded via `AuditLogService`; chain verifies |
| T-71 | Audit | Detail content | No password/token/hash/full email |
| T-72 | Logs | Capture logs during all login/refresh/logout/denied tests | Contain **none** of: the password, refresh token, access token, hashes, signing key |
| T-73 | Startup | `JWT_STAFF_SECRET` missing or < 32 bytes | Application fails fast |
| T-74 | Arch | ArchUnit | No controller→repository; only sanctioned unscoped lookups; module boundaries hold |
| T-75 | DB | App starts on Flyway-migrated schema, `ddl-auto` not `update` | Passes |

## 3. Fixtures and test-only code

- **Test-only controller** under `src/test` (or `@Profile("test")`), so RBAC/scope can be proven before Phases 4/5 land:
  - `GET /test/platform-only`, `/test/tenant-admin-only`, `/test/manager-and-above`, `/test/any-staff`
  - `GET /test/records/{id}` backed by an in-memory tenant-scoped store → returns 404 across tenants/locations.
- **Data builders:** two tenants (A, B); users for each role; a location-scoped user in Location 1 and Location 2; org-scoped user; locked user; disabled user; platform admin (no tenant).
- **Clock:** inject a mutable `Clock` bean in tests.
- **Containers:** PostgreSQL 16 + Redis, shared across the suite.
- **Token factory:** helper that can mint intentionally bad tokens (expired, wrong type, wrong audience, wrong key, `alg=none`, tampered).

## 4. Log-hygiene test (T-72) — approach

Attach an in-memory Logback appender for the duration of the flow tests. After the run, assert the combined output contains none of the literal secrets used in the test (the plaintext password, the issued refresh token, the issued access token string, the BCrypt hash prefix `$2`, the signing secret).

## 5. Coverage expectation

All rows above pass. Minimum line coverage for the `identity` and `security` packages: agree with Rabeesh `[ASSUMPTION: ≥ 80 %]`; branch coverage matters more than the number — every `ErrorCode` listed in file 03 §6.1 must be produced by at least one test.

## 6. Evidence for Phase 8 (Rabeesh)

Deliver: (a) test report, (b) Postman collection + environment template (no secrets): *login ok, login bad password, login unknown email, locked, refresh, refresh reuse, logout, reuse after logout, wrong role, cross-tenant, cross-location, expired/tampered token*, (c) audit-chain verification output after the run, (d) seed-data description (accounts per role/scope; passwords supplied via environment, never committed).

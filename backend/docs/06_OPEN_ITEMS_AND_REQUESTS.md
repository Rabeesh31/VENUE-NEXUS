# 06 — Open Items, Requests and Assumption Register

This file answers: *"what must be confirmed or supplied so integration is error-free?"*

## A. Requests to Phase 1 (Rithik) — please share

Tick each when received. The agent must **stop** if an `[HANDOFF]` item is missing.

| # | Item | Why Phase 3 needs it | Have it? |
|---|------|----------------------|:-:|
| A1 | `docs/security.md` (frozen) | Claims table with **actual values** of `aud`/`iss`, `typ` values, algorithm (HS256 vs RS256), lockout policy, role enum, role matrix, refresh-token delivery | ☐ |
| A2 | `docs/api-contract.md` — auth section | Exact request/response JSON for login/refresh/logout | ☐ |
| A3 | `ErrorCode` enum **source** (full list + HTTP status for each) | Confirm the AUTH_* codes' HTTP statuses (especially `AUTH_ACCOUNT_LOCKED`, `LOCATION_SCOPE_VIOLATION`) | ☐ |
| A4 | `ApiException`, `ApiError`, `GlobalExceptionHandler` source | Same JSON from filters; constructor/factory signatures | ☐ |
| A5 | `TraceIdFilter` source (confirm `getCurrentTraceId()` is static and its package) | `traceId` in 401/403 | ☐ |
| A6 | `AuditLogService`, `AuditEvent` (all factory methods + parameters), `AuditEventType` (full list) | Need exact `record(...)` arguments; confirm `AUTH_LOCKOUT`, `AUTH_REFRESH_REUSE_DETECTED`, `AUTH_TOKEN_REJECTED`, `ACCESS_DENIED_*` exist; confirm whether a **null tenant** is allowed | ☐ |
| A7 | `backend/pom.xml` | Which JWT library, Redis starter, Testcontainers modules are already present | ☐ |
| A8 | Base package name + module/package conventions | Place files correctly | ☐ |
| A9 | `docker-compose.yml` + env template | Redis service; `JWT_STAFF_SECRET`, `REDIS_*`, `CORS_ALLOWED_ORIGINS` names | ☐ |
| A10 | ArchUnit test sources | Know exact rules; add the pre-login lookup exception | ☐ |
| A11 | `application*.yml` (profiles) | Where to put `security.*` properties | ☐ |
| A12 | Decision on `AUTH_LOGOUT` audit event | Not in the PRD list; add or omit | ☐ |

## B. Requests to Phase 2 (Kavisharmitha)

| # | Item | Why | Have it? |
|---|------|-----|:-:|
| B1 | Flyway `V2` SQL | Real column names, types, constraints for `app_user`, `refresh_token` | ☐ |
| B2 | `AppUser`, `RefreshToken` entities + repositories + enums (`Role`, user status) | Consume, don't redefine | ☐ |
| B3 | Named pre-login queries (user by email, ignoring case; refresh token by hash with row lock; revoke by family) | Login precedes tenant knowledge | ☐ |
| B4 | Atomic failed-login increment (or `@Version`) | Race-safe lockout | ☐ |
| B5 | Index/constraint list for `app_user` (case-insensitive unique email) and `refresh_token` (hash unique, family index) | Behaviour and performance | ☐ |

## C. Requests to others

| # | From | Item |
|---|------|------|
| C1 | Ashwin | Confirm he will use the shared `PasswordEncoder`; confirm who owns the unlock endpoint; confirm permitted-locations endpoint for the location switcher |
| C2 | Lackshana | Confirm login/refresh/logout JSON, refresh-token transport, 401-handling strategy (refresh once, then login) |
| C3 | Rabeesh | Seed data policy (pilot tenant/location open decision); Postman format |

## D. Open decisions (with the default the agent must use meanwhile)

| ID | Decision | Default used until confirmed | Isolation (so a change is trivial) |
|----|----------|------------------------------|------------------------------------|
| D-P3-1 | HTTP status of `AUTH_ACCOUNT_LOCKED` | Whatever `ErrorCode` defines; if undefined propose **423** (PRD uses 423 for `OTP_LOCKED`) | `ErrorCode` only |
| D-P3-2 | Out-of-scope **record** access → `NOT_FOUND` (PRD) vs `LOCATION_SCOPE_VIOLATION` (Phase 1 handoff lists it) | **404 for records addressed by id**; `LOCATION_SCOPE_VIOLATION` only when a client explicitly asks for a location it may not use (e.g. switcher/filter parameter) | `ScopeEnforcer` |
| D-P3-3 | Refresh token in JSON body vs `HttpOnly` cookie | JSON body | Auth controller + CORS |
| D-P3-4 | HS256 vs RS256 | HS256 | `StaffJwtService` |
| D-P3-5 | Lockout thresholds | 5 failures / 15 min lock | Config |
| D-P3-6 | Who exposes the admin-unlock HTTP endpoint | Phase 3 builds `AccountUnlockService`; route added only after owner confirmed | Controller |
| D-P3-7 | Auditing a failed login for an **unknown email** (no tenant → per-tenant hash chain) | Do **not** write an audit row; log WARN with traceId + metric (no raw email) | `AuthService` |
| D-P3-8 | Redis down while checking denylist | **Fail closed** (reject) + metric | `TokenDenylist` |
| D-P3-9 | Final role enum + permission matrix (mentor approval pending) | Matrix in file 03 §9 | `Roles`, `SecurityExpressions` |
| D-P3-10 | Sanctioned unscoped repository lookups for pre-login | Exactly: user-by-email, refresh-token-by-hash/family | ArchUnit allow-list |
| D-P3-11 | Message text for each error (safe wording) | Generic, no hints | `ErrorCode` messages |
| D-P3-12 | Does a **suspended tenant** block login/refresh? | Yes | `AuthService` |
| D-P3-13 | Should refresh be refused for a user whose role/scope changed? | No — issue new token with current DB role/scope | `RefreshTokenService` |

Project-level open decisions that also touch this phase (from the Implementation Plan): mentor approval of the role enum and matrix; API/error/event contract approval; D-1 Redis scope (Redis stays for denylist unless the Team Lead removes it — if removed, the denylist design must change: use short-lived tokens + DB revocation).

## E. Assumption register (agent must verify each in P3-00)

| # | Assumption | Verified? |
|---|------------|:-:|
| E1 | `aud` = `venue-nexus-staff`, `iss` = `venue-nexus` | ☐ |
| E2 | Claim names exactly: `uid, typ, rol, tid, oid, lid, did, jti, aud, iss` (+`iat`, `exp`) | ☐ (handoff states these) |
| E3 | Login path `/api/v1/auth/login` | ☐ |
| E4 | Login/refresh response JSON shape (file 03 §3) | ☐ |
| E5 | `ErrorCode` HTTP statuses (401 for token errors, 403 for `ACCESS_DENIED`) | ☐ |
| E6 | Redis key `auth:denylist:jti:<jti>` | ☐ |
| E7 | `app_user` has: failed-login count, lock timestamp, last-login, role, status; `refresh_token` has: hash, family id, expiry, revoked-at, replaced-by | ☐ (TRD §6.4 lists these) |
| E8 | BCrypt strength 12 | ☐ |
| E9 | JWT library already in `pom.xml` | ☐ |
| E10 | Test-only controllers permitted in `src/test` | ☐ |
| E11 | Seed users supplied by dev/test profile only, passwords via environment | ☐ |
| E12 | `AuditLogService` accepts a user id and a trace id automatically (from MDC) | ☐ |

## F. Source-document inconsistencies to be aware of

1. **Allocation says `POST /auth/login`; PRD v1.1 says all endpoints are `/api/v1/...`.** → use `/api/v1/auth/login` unless `api-contract.md` says otherwise.
2. **PRD base error codes** (`UNAUTHENTICATED`, `FORBIDDEN`, `NOT_FOUND`) vs **Phase 1's finer AUTH_* codes.** Phase 1's list is the more recent handoff; treat the AUTH_* codes as authoritative for Phase 3 and map them in `api-contract.md`.
3. **PRD says other-location access → 404;** Phase 1 lists `LOCATION_SCOPE_VIOLATION`. See D-P3-2.
4. **PRD requires admin unlock (FR-ID-07)** but no phase step assigns the endpoint. See D-P3-6.
5. **Token denylist uses Redis;** the TRD says Sprint 1 Redis is "local infrastructure; does not implement customer session state" — the denylist is staff-token state and is explicitly in Phase 3 scope (3.3), so it is consistent, but confirm with Rabeesh (D-1).

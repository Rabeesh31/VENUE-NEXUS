# 03 — Specification: Tokens, Endpoints, Errors, Audit, Config

> Anything tagged `[ASSUMPTION]` or `[DECISION]` must be verified against `docs/security.md` and `docs/api-contract.md`. If those documents disagree with this file, **they win** — and record the difference in the PR.

## 1. Token overview `[FROZEN]` (PRD v1.1 §6.1.6)

| Token | Lifetime | Format | Key | Accepted by |
|---|---|---|---|---|
| Staff access | **15 minutes** | JWT, `typ=STAFF` | Staff signing key (`JWT_STAFF_SECRET`) | Staff endpoints |
| Staff refresh | **7 days, rotating** | **Opaque** (not a JWT); stored as SHA-256 hash | n/a | Refresh endpoint only |

Rules `[FROZEN]`:
- Signing, encryption, HMAC and OTP keys are all **different**. Secrets ≥ **32 random bytes**.
- Tokens never carry prices, business data or personal data.
- The server rejects with **401**: missing, expired, invalid signature, algorithm `none`, wrong type, wrong audience, or denylisted tokens.
- Refresh rotation issues a **new** refresh token on every use. **Reuse of an old token revokes the whole family** and is audited.

## 2. Access-token claims `[HANDOFF names; values ASSUMPTION until security.md is read]`

| Claim | Meaning | Type | Presence |
|---|---|---|---|
| `uid` | User id (`app_user.id`) | UUID string | always |
| `typ` | Token type, value `STAFF` | string | always |
| `rol` | Role enum name | string | always |
| `tid` | Tenant id | UUID string | omitted for `PLATFORM_ADMIN` |
| `oid` | Organisation id (scope) | UUID string | omitted if not org/location scoped |
| `lid` | Location id (scope) | UUID string | omitted if not location scoped |
| `did` | Department id (scope) | UUID string | omitted if none |
| `jti` | Unique token id (denylist key) | UUID string | always |
| `aud` | Audience | string | always — proposed `venue-nexus-staff` `[ASSUMPTION]` |
| `iss` | Issuer | string | always — proposed `venue-nexus` `[ASSUMPTION]` |
| `iat` / `exp` | Issued-at / expiry (`exp = iat + 15 min`) | epoch seconds | always |

- Omit absent claims; never emit `null`.
- Read `aud`/`iss` from config (`security.jwt.audience`, `security.jwt.issuer`), **not** hard-coded.
- Algorithm: **HS256** `[DECISION D-P3-4]`. Pin it; reject `none` and any other.

## 3. `POST /api/v1/auth/login`  (public)

Path prefix: all endpoints are `/api/v1/...` `[FROZEN PRD §9.3]`. (The allocation writes `/auth/login`; use `/api/v1/auth/login` unless `api-contract.md` says otherwise.)

**Request**
```json
{ "email": "manager@example.com", "password": "••••••••" }
```

**Success `200`** `[ASSUMPTION — must match api-contract.md and Phase 6's OpenAPI client]`
```json
{
  "accessToken": "<jwt>",
  "refreshToken": "<opaque>",
  "tokenType": "Bearer",
  "expiresIn": 900,
  "user": {
    "id": "uuid",
    "displayName": "string",
    "role": "VENUE_MANAGER",
    "tenantId": "uuid|null",
    "organisationId": "uuid|null",
    "locationId": "uuid|null",
    "departmentId": "uuid|null"
  }
}
```
- Refresh-token transport (JSON body vs `HttpOnly` cookie) is `[DECISION D-P3-3]`; default **body**. UI/UX §7.1 says "according to the approved security design".
- The client never sends `tenantId` — it only *receives* it.

**Failures** (all in the standard error body, §6)

| Situation | ErrorCode `[HANDOFF]` | Notes |
|---|---|---|
| Blank/invalid input | `VALIDATION_ERROR` | PRD list |
| Unknown email **or** wrong password | `AUTH_INVALID_CREDENTIALS` | **Identical** response for both |
| Account locked | `AUTH_ACCOUNT_LOCKED` | UI shows a "locked" state; HTTP status from `ErrorCode` |
| Inactive/disabled user, suspended tenant | `AUTH_INVALID_CREDENTIALS` `[ASSUMPTION]` | Confirm; avoid leaking status |

Side effects: counters, `last_login`, refresh row (hash only), audit `AUTH_LOGIN` / `AUTH_LOGIN_FAILED`.

## 4. `POST /api/v1/auth/refresh`  (public)

**Request:** `{ "refreshToken": "<opaque>" }`
**Success `200`:** same shape as login (new access token **and new refresh token**).

| Situation | ErrorCode |
|---|---|
| Unknown hash / malformed | `AUTH_TOKEN_INVALID` |
| Past `expires_at` | `AUTH_TOKEN_EXPIRED` |
| Already used / revoked (reuse) | `AUTH_REFRESH_REUSE_DETECTED` + family revoked + audit |
| User now disabled/locked/tenant suspended | `AUTH_INVALID_CREDENTIALS` `[ASSUMPTION]` or `AUTH_ACCOUNT_LOCKED` |

Behaviour: single transaction, same family, old row revoked with replacement reference, user re-read from DB so role/scope changes take effect at next refresh. The family revocation on reuse **must commit** despite the thrown exception.

## 5. `POST /api/v1/auth/logout`  (authenticated)

**Request:** `{ "refreshToken": "<opaque>" }` → **`204`**.
Effects: revoke the family of that refresh token (only if it belongs to the caller); denylist the caller's access-token `jti` until it would have expired. Idempotent.

## 6. Error body `[FROZEN PRD §9.3]`

```json
{
  "timestamp": "2026-11-07T12:00:00Z",
  "status": 401,
  "error": "AUTH_TOKEN_EXPIRED",
  "message": "…safe, generic text…",
  "path": "/api/v1/…",
  "traceId": "trace_12345"
}
```
- `error` carries the `ErrorCode` name. `traceId` comes from `TraceIdFilter.getCurrentTraceId()`; the same value is returned in the `X-Trace-Id` response header `[HANDOFF]`.
- **Messages must be safe:** no stack traces, SQL, user existence hints, token content, or which claim failed in detail beyond the code.
- The same body must be produced by `GlobalExceptionHandler` (MVC) **and** by the security entry-point/denied handlers (filter level).

### 6.1 ErrorCodes Phase 3 uses `[HANDOFF]`

`AUTH_INVALID_CREDENTIALS`, `AUTH_ACCOUNT_LOCKED`, `AUTH_TOKEN_EXPIRED`, `AUTH_TOKEN_INVALID`, `AUTH_TOKEN_WRONG_TYPE`, `AUTH_TOKEN_INVALID_AUDIENCE`, `AUTH_TOKEN_REVOKED`, `AUTH_REFRESH_REUSE_DETECTED`, `ACCESS_DENIED`, `LOCATION_SCOPE_VIOLATION`, plus the PRD base codes `VALIDATION_ERROR`, `UNAUTHENTICATED` (401), `FORBIDDEN` (403), `NOT_FOUND` (404).

Throw as `new ApiException(ErrorCode.AUTH_…)` `[HANDOFF]`. **HTTP status comes from `ErrorCode`** — never hard-code statuses in Phase 3 code.

### 6.2 Expected HTTP outcomes (for tests)

| Case | Expected |
|---|---|
| Valid login | 200 |
| Invalid credentials | 401 |
| Missing/expired/tampered/wrong-type/wrong-audience/denylisted token | 401 |
| Valid token, insufficient role | 403 |
| Another tenant's record | 404 (no data, no hint) `[FROZEN PRD §12.1]` |
| Location-scoped user, other location's record | 404 `[FROZEN PRD §12.1]`; `LOCATION_SCOPE_VIOLATION` for explicit location params `[DECISION D-P3-2]` |
| Locked account | status defined by `ErrorCode.AUTH_ACCOUNT_LOCKED` `[DECISION D-P3-1]` |

## 7. Redis keys `[ASSUMPTION — confirm naming with security.md]`

| Purpose | Key | Value | TTL |
|---|---|---|---|
| Access-token denylist | `auth:denylist:jti:<jti>` | `1` | remaining access-token lifetime |

No other Redis state in Phase 3. If Redis is down, the denylist check **fails closed** `[DECISION D-P3-8]`.

## 8. `SecurityConfig` rules

| Concern | Rule |
|---|---|
| Session | `STATELESS` |
| CSRF | disabled (bearer-token API) |
| Form login / HTTP Basic / default logout | disabled |
| `permitAll` | `POST /api/v1/auth/login`, `POST /api/v1/auth/refresh`, health endpoint; OpenAPI + Swagger UI **non-prod only** `[ASSUMPTION]` |
| Authenticated | `/api/v1/**` |
| Final rule | `anyRequest().denyAll()` **(deny-by-default)** |
| Filter order | `TraceIdFilter` (HIGHEST_PRECEDENCE) → … → `StaffJwtFilter` before `UsernamePasswordAuthenticationFilter` |
| Errors | JSON `AuthenticationEntryPoint` (401) and `AccessDeniedHandler` (403) using Phase 1's `ApiError` |
| CORS | origins from `CORS_ALLOWED_ORIGINS`; allowed headers `Authorization`, `Content-Type`, `Idempotency-Key`; **exposed header `X-Trace-Id`** |
| Method security | `@EnableMethodSecurity` |
| OpenAPI | declare `bearerAuth` (HTTP bearer, JWT) so Phase 6's generated client attaches the token |
| Not enabled | `/api/v1/public/**`, `/api/v1/portal/**` (Sprint 2) |

## 9. Roles, matrix and method guards

Roles `[FROZEN]`: `PLATFORM_ADMIN`, `TENANT_ADMIN`, `VENUE_MANAGER`, `SUPERVISOR`, `STAFF`. (Customer/Guest has no Sprint 1 functionality.)
The **final role-to-permission matrix is not yet approved** (mentor + Team Lead). Centralise everything in `Roles` / `SecurityExpressions` so approval changes one file.

Authorities: `ROLE_<ROLE_NAME>`; use `hasRole(...)`/`hasAnyRole(...)` consistently (do **not** mix `hasAuthority('PLATFORM_ADMIN')` and `hasRole('PLATFORM_ADMIN')`).

### Proposed Sprint 1 matrix `[ASSUMPTION — derived from PRD §2.1 / S1-FR-01…09; NOT approved]`

| Capability | PLATFORM_ADMIN | TENANT_ADMIN | VENUE_MANAGER | SUPERVISOR | STAFF |
|---|:-:|:-:|:-:|:-:|:-:|
| Create / suspend / reactivate tenant | ✔ | | | | |
| Organisations & locations | | ✔ | | | |
| Spaces & departments | | ✔ | ✔ | | |
| Staff users (within scope) | | ✔ | ✔ | | |
| Resources: create/edit | | ✔ | ✔ | | |
| Resources: read | | ✔ | ✔ | ✔ | ✔ |
| QR issue / revoke / reissue | | ✔ | ✔ | | |
| Unlock a locked user | ✔ | ✔ | | | |

Proposed reusable guards (names illustrative):
```java
public final class SecurityExpressions {
    public static final String PLATFORM_ONLY        = "hasRole('PLATFORM_ADMIN')";
    public static final String TENANT_ADMIN_ONLY    = "hasRole('TENANT_ADMIN')";
    public static final String MANAGER_AND_ABOVE    = "hasAnyRole('TENANT_ADMIN','VENUE_MANAGER')";
    public static final String ANY_STAFF_ROLE       = "hasAnyRole('TENANT_ADMIN','VENUE_MANAGER','SUPERVISOR','STAFF')";
}
```
Usage: `@PreAuthorize(SecurityExpressions.MANAGER_AND_ABOVE)` on service methods (and controllers where useful). Scope is **additionally** applied inside the service via `ScopeEnforcer` — role alone is never enough.

## 10. Scope resolution `[FROZEN PRD §7.5]`

| User | Claims | Access |
|---|---|---|
| Platform admin | no `tid` | Platform operations only (tenant lifecycle) |
| Tenant-level | `tid` | Whole tenant |
| Organisation-scoped | `tid`, `oid` | That organisation |
| Location-scoped | `tid` (+`oid`), `lid` | That location only; optional `did` narrows department |

Every tenant-owned query: **tenant filter always**; add org/location filter per scope. Out-of-scope records look **non-existent** (404).

## 11. Configuration properties `[ASSUMPTION names; env vars FROZEN]`

| Property | Default | Notes |
|---|---|---|
| env `JWT_STAFF_SECRET` | — | **required**, ≥ 32 bytes, never committed `[FROZEN]` |
| env `REDIS_HOST/PORT/PASSWORD` | — | `[FROZEN]` |
| env `CORS_ALLOWED_ORIGINS` | — | `[FROZEN]` |
| `security.jwt.issuer` | `venue-nexus` | |
| `security.jwt.audience` | `venue-nexus-staff` | |
| `security.jwt.access-ttl` | `PT15M` | `[FROZEN]` |
| `security.jwt.leeway` | `PT0S`–`PT30S` | |
| `security.refresh.ttl` | `P7D` | `[FROZEN]` |
| `security.login.max-failed-attempts` | `5` | `[ASSUMPTION]` |
| `security.login.lockout-duration` | `PT15M` | `[ASSUMPTION]` |
| `security.password.bcrypt-strength` | `12` | `[ASSUMPTION]`; dummy hash uses the same strength |

Secrets are read from environment only. The application **must fail fast** on startup if `JWT_STAFF_SECRET` is absent or too short. Add the same names (without values) to the env template.

## 12. Audit events `[HANDOFF + PRD §6.1.10]`

| Event | When |
|---|---|
| `AUTH_LOGIN` `[HANDOFF]` | successful login |
| `AUTH_LOGIN_FAILED` `[HANDOFF]` | failed login for a **known** user (see D-P3-7 for unknown email) |
| `AUTH_LOCKOUT` (PRD) | lock applied |
| `AUTH_REFRESH_REUSE_DETECTED` (PRD) | reuse → family revoked |
| `AUTH_TOKEN_REJECTED` (PRD) | rejected token where tenant is determinable |
| `ACCESS_DENIED_ROLE/TENANT/SCOPE` (PRD) | authorisation denial |

Call shape `[HANDOFF]`: `auditLogService.record(AuditEvent.success(tenantId, userId, AuditEventType.AUTH_LOGIN, …))`. The trailing `…` (detail/outcome arguments) must be read from the real `AuditEvent` class — **do not guess it**. Detail content: no secrets, no tokens, no password material, minimal personal data.

## 13. Logging rules `[FROZEN PRD §6.5]`

Logs are JSON with `traceId`, `tenantId`, `userId`. They must **never** contain: passwords or hashes; access, refresh or session tokens; API keys and secrets; full emails (mask only). A log-capture test in Phase 3 must prove this.

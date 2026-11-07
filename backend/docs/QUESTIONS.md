# VENUE NEXUS — Phase 3 Questions & Open Decisions

This document tracks items that are open or subject to confirmation from Phase 1/Phase 2, along with the approved defaults adopted from `docs/06_OPEN_ITEMS_AND_REQUESTS.md`.

## Open Decisions & Defaults Adopted

1. **D-P3-1: HTTP Status for `AUTH_ACCOUNT_LOCKED`**
   - **Status:** Default adopted.
   - **Default:** HTTP `423 Locked`.
   - **Isolation:** Configured strictly via `ErrorCode.AUTH_ACCOUNT_LOCKED`.

2. **D-P3-2: Out-of-Scope Record Access Response**
   - **Status:** Default adopted.
   - **Default:** Return `404 Not Found` for direct resource lookups by ID (to prevent information leakage). Return `LOCATION_SCOPE_VIOLATION` (HTTP 403) only when explicit location switcher or query parameters conflict with the user's assigned scope.
   - **Isolation:** Managed in `ScopeEnforcer`.

3. **D-P3-3: Refresh Token Transport**
   - **Status:** Default adopted.
   - **Default:** JSON body in `POST /api/v1/auth/login` and `POST /api/v1/auth/refresh`.

4. **D-P3-4: JWT Signing Algorithm**
   - **Status:** Default adopted.
   - **Default:** `HS256` using secret from `JWT_STAFF_SECRET` (minimum 32 bytes / 256 bits).

5. **D-P3-5: Lockout Policy Thresholds**
   - **Status:** Default adopted.
   - **Default:** 5 consecutive failed attempts triggering a 15-minute lockout (`PT15M`). Configurable via properties.

6. **D-P3-6: Ownership of Admin Unlock HTTP Endpoint**
   - **Status:** Default adopted.
   - **Default:** Phase 3 provides the core `AccountUnlockService.unlock(UUID userId)`. The REST endpoint will be wired to Phase 4 staff management endpoints or exposed upon mutual agreement.

7. **D-P3-7: Audit Logging for Unknown Email Failed Logins**
   - **Status:** Default adopted.
   - **Default:** Do not write a tenant-chained audit row (since tenant is unknown). Log a `WARN` message with trace ID and increment the failure metric.

8. **D-P3-8: Redis Outage Behavior during Token Denylist Check**
   - **Status:** Default adopted.
   - **Default:** Fail closed (reject request with HTTP 401 `AUTH_TOKEN_REVOKED` / security exception) and increment failure metrics.

9. **D-P3-9: Role Enum & Permission Matrix**
   - **Status:** Default adopted.
   - **Default:** `StaffRole`: `PLATFORM_ADMIN`, `TENANT_ADMIN`, `VENUE_MANAGER`, `SUPERVISOR`, `STAFF`. Permissions centralized in `Roles` and `SecurityExpressions`.

10. **D-P3-10: Sanctioned Unscoped Pre-Login Repository Lookups**
    - **Status:** Default adopted.
    - **Default:** Explicitly named methods:
      - `AppUserRepository.findForAuthenticationByEmail(String email)`
      - `RefreshTokenRepository.findByTokenHashForRotation(String tokenHash)`
      - `RefreshTokenRepository.revokeFamily(UUID familyId, Instant revokedAt)`

11. **D-P3-11: Generic & Safe Error Messages**
    - **Status:** Default adopted.
    - **Default:** Identical generic messages for invalid email or password ("Invalid email or password"). No details on internal checks leaked in API responses.

12. **D-P3-12: Suspended Tenant Blocking**
    - **Status:** Default adopted.
    - **Default:** Users belonging to a suspended tenant are rejected with generic credentials error (`AUTH_INVALID_CREDENTIALS`). `PLATFORM_ADMIN` is exempt as they have no tenant.

13. **D-P3-13: Refresh Token Role/Scope Changes**
    - **Status:** Default adopted.
    - **Default:** During refresh token rotation, user is re-queried from the database. The new access token reflects the current database role and scope.


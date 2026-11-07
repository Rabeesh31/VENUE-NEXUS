# 07 — Antigravity Prompts

Use these in order. Each prompt assumes the pack is in the repo at `docs/phase3/` and the Phase 1 / Phase 2 code has been pulled from `develop`.

---

## MASTER PROMPT (paste first, once)

```
You are the implementation agent for VENUE NEXUS Sprint 1, Phase 3 — Staff Identity & Security
(Java 21, Spring Boot 3, Spring Security, JPA, Flyway, PostgreSQL 16, Redis, Maven).

READ FIRST, IN THIS ORDER, before writing any code:
docs/phase3/00_README_START_HERE.md
docs/phase3/01_CONTEXT_AND_SCOPE.md
docs/phase3/03_SPEC_TOKENS_ENDPOINTS_ERRORS.md
docs/phase3/04_DEPENDENCIES_AND_INTEGRATION.md
docs/phase3/02_TASKS_PHASE3.md
docs/phase3/05_TEST_PLAN.md
docs/phase3/06_OPEN_ITEMS_AND_REQUESTS.md
docs/phase3/08_DEFINITION_OF_DONE.md
Also read the repo's docs/security.md, docs/api-contract.md, docs/db-schema.md if present.

RULES
1. Never invent a class, enum value, column, endpoint shape or error code. If something is not in the
   repo or the pack, STOP and list it under "BLOCKERS" instead of guessing.
2. Reuse Phase 1's ErrorCode, ApiException, ApiError, GlobalExceptionHandler, TraceIdFilter,
   AuditLogService. Reuse Phase 2's entities/repositories. Do not duplicate them.
3. Controller -> Service -> Repository. No controller touches a repository. Respect ArchUnit.
4. Tenant id comes only from the validated token (TenantContext). Never from request body/query/path.
5. No secrets in code, logs, audit detail, exceptions or responses. Passwords, hashes, access/refresh
   tokens and signing keys must never be logged.
6. Do NOT add Flyway migrations and do NOT use ddl-auto=update. Report missing columns instead.
7. Do not name any Redis client library in comments or docs; say "Redis".
8. Anything tagged [ASSUMPTION] or [DECISION] in the pack: implement the stated default behind
   config/constants, and list it in the PR description.
9. Work task-by-task in the order given in 02_TASKS_PHASE3.md. One feature branch and one small PR per
   task group. Update docs/security.md and docs/api-contract.md in the same PR as any contract change.
10. After each task: build, run tests, and report results. Do not proceed if tests fail.

FIRST ACTION: execute task P3-00 (pre-flight) only, and output a GAP REPORT:
 (a) files/classes found, with their paths and the exact signatures I need,
 (b) files/classes missing,
 (c) differences between the repo and the pack,
 (d) the exact V2 column names for app_user and refresh_token.
Then wait for my confirmation before starting P3-01.
```

---

## P3-01 prompt — public security contracts (first PR)

```
Implement task P3-01 from docs/phase3/02_TASKS_PHASE3.md: TenantContext, ScopeContext, CurrentStaff,
Roles, SecurityExpressions, ScopeEnforcer (interface + stub implementation) and the shared
PasswordEncoder bean (BCrypt, strength from config). Use the REAL role enum from the repo/security.md.
Write Javadoc for every public method (throws, thread-safety, "never trust client tenant").
Add ArchUnit/compile checks, add a "Public security API for module authors" section to docs/security.md.
Goal: Phase 4 (Ashwin) and Phase 5 (Monisha) can compile against these types today.
Branch: feature/phase3-security-contracts. Do not implement login yet.
```

## P3-02 / P3-03 prompt — lookup, verification, lockout

```
Implement P3-02 and P3-03. Use the Phase 2 AppUser entity and the named pre-login lookup
(case-insensitive email). Implement BCrypt verification with timing equalisation (dummy hash of the same
strength for unknown emails), identical AUTH_INVALID_CREDENTIALS for unknown email and wrong password,
failed-login counting with an atomic update, lockout (config: max attempts, duration), automatic expiry
of the lock, reset on success, and AccountUnlockService.unlock(userId) (tenant-scoped, @PreAuthorize).
Do NOT expose an unlock HTTP route yet. Inject Clock. Write unit tests T-02..T-13 from 05_TEST_PLAN.md.
Never log or return the password or hash.
```

## P3-04 prompt — JWT service

```
Implement P3-04: StaffJwtService (issue + parseAndValidate) using the JWT library already in pom.xml.
Claims: uid, typ=STAFF, rol, tid, oid, lid, did, jti, aud, iss, iat, exp(15 min). Omit absent claims.
Pin the algorithm (HS256 default), reject alg=none and any other algorithm. Key from JWT_STAFF_SECRET;
fail fast on startup if missing or shorter than 32 bytes. Map failures exactly:
invalid/tampered/none/wrong-key -> AUTH_TOKEN_INVALID; expired -> AUTH_TOKEN_EXPIRED;
typ != STAFF -> AUTH_TOKEN_WRONG_TYPE; aud/iss mismatch -> AUTH_TOKEN_INVALID_AUDIENCE.
Provide a test helper that mints deliberately bad tokens. Tests T-20..T-28, T-73.
```

## P3-08 / P3-09 prompt — filter, handlers, SecurityConfig

```
Implement P3-08 and P3-09. StaffJwtFilter (OncePerRequestFilter) validates the bearer token, checks the
denylist (TokenDenylist interface; Redis implementation comes in P3-07 — use an in-memory fake for now),
populates SecurityContext/TenantContext/ScopeContext and ALWAYS clears all three in finally.
Create a JSON AuthenticationEntryPoint (401) and AccessDeniedHandler (403) that reuse Phase 1's
ApiError so the JSON fields are identical to GlobalExceptionHandler output, with traceId from
TraceIdFilter.getCurrentTraceId(). Make sure the filter is registered ONCE (inside the security chain).
SecurityConfig: stateless, CSRF off, permitAll for POST /api/v1/auth/login and /refresh and health,
OpenAPI/Swagger only in non-prod, /api/v1/** authenticated, final anyRequest().denyAll(), CORS from
CORS_ALLOWED_ORIGINS exposing X-Trace-Id, @EnableMethodSecurity, bearerAuth in OpenAPI.
Add test-only controllers (src/test) and tests T-26, T-50..T-53, T-61..T-63.
```

## P3-05 prompt — login

```
Implement P3-05: POST /api/v1/auth/login per docs/phase3/03 §3 and the repo's api-contract.md
(api-contract.md wins if it differs; note the difference). On success: reset counters, update last-login,
issue access JWT and an opaque refresh token (32 bytes SecureRandom, base64url); persist ONLY the SHA-256
hash with a new family id and 7-day expiry; audit AUTH_LOGIN via AuditLogService using the real
AuditEvent factory; return tokens + user summary. On failure audit AUTH_LOGIN_FAILED for known users.
Add OpenAPI annotations. Tests T-01..T-14, T-70..T-72.
```

## P3-06 / P3-07 prompt — refresh, logout, denylist

```
Implement P3-06 and P3-07. Refresh: hash lookup with a pessimistic row lock; unknown -> AUTH_TOKEN_INVALID;
expired -> AUTH_TOKEN_EXPIRED; reuse -> revoke the whole family, audit AUTH_REFRESH_REUSE_DETECTED and
return AUTH_REFRESH_REUSE_DETECTED — the family revocation MUST commit even though an exception is thrown
(noRollbackFor or REQUIRES_NEW) and a test must prove it. Normal case: one transaction, mark old revoked
with replacement reference, insert next token in the same family, re-read the user from the DB
(status, lock, tenant status, role, scope). Logout: revoke the family (must belong to the caller) and
denylist the access token jti in Redis with TTL = remaining lifetime; idempotent; 204.
Redis down -> fail closed + metric. Tests T-30..T-46 (Testcontainers PostgreSQL + Redis).
```

## P3-10 prompt — authorisation and scope

```
Implement P3-10: replace the ScopeEnforcer stub. Rules: tenant-level user -> any record in own tenant;
org-scoped -> record.organisationId == oid; location-scoped -> record.locationId == lid; platform admin ->
platform operations only. Out-of-scope record access must look non-existent (404-style). Centralise the
role matrix in Roles/SecurityExpressions. Ensure deny-by-default (anyRequest().denyAll() plus method
security). Audit ACCESS_DENIED_* where AuditEventType provides them. Add usage examples to
docs/security.md for Phase 4 and 5. Tests T-50..T-61.
```

## FINAL prompt — verification and hand-over

```
Run the full verification described in docs/phase3/08_DEFINITION_OF_DONE.md:
1) mvn verify (unit + Testcontainers + ArchUnit) 2) the smoke script in 04_DEPENDENCIES_AND_INTEGRATION.md §5
3) the log-hygiene test 4) confirm docs/security.md and docs/api-contract.md are updated 5) produce:
 - a table mapping each test ID T-xx to its test class/method and result,
 - the list of [ASSUMPTION]/[DECISION] items actually used,
 - the Postman collection + environment template (no secrets),
 - a short note for Ashwin, Monisha and Lackshana describing exactly what they can now call/consume.
Do not mark anything done that is not proven by a passing test.
```

---

## Review prompt (use before each merge)

```
Review this PR against docs/phase3/08_DEFINITION_OF_DONE.md. Specifically check: no secrets in code/logs/
audit; no tenantId from the client; no controller->repository; error JSON identical for filter-level and
MVC-level errors; family revocation survives the exception; contexts cleared in finally; filter registered
once; HTTP statuses come from ErrorCode; unknown-email and wrong-password responses are identical;
docs updated. List every violation with file and line.
```

## Stop conditions (agent must halt and report)

- A `[HANDOFF]` class/enum/endpoint is missing or has a different signature.
- A V2 column needed by the pack does not exist.
- `security.md` contradicts the pack on claims, algorithm, lifetimes or lockout.
- A required change would need a new Flyway migration.
- Any step would require logging or returning a secret.

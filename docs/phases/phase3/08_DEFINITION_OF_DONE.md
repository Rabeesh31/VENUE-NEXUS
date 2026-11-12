# 08 — Definition of Done and Sign-off

## 1. Per-PR checklist `[FROZEN: Implementation Plan §10, adapted]`

- [ ] Matches the approved requirement and contract (or the difference is documented in the PR)
- [ ] On a `feature/phase3-<name>` branch; PR into `develop`; reviewed
- [ ] Unit/API/integration tests for the change pass
- [ ] Tenant and location scope checked where applicable
- [ ] **No secrets** committed or logged (password, hash, access/refresh token, signing key)
- [ ] Docs updated in the same PR (`security.md`, `api-contract.md`, OpenAPI annotations)
- [ ] ArchUnit passes (no controller→repository; module boundaries; sanctioned unscoped lookups only)
- [ ] `[ASSUMPTION]` / `[DECISION]` items used are listed in the PR description
- [ ] After merge into `develop`: full build + tests + smoke script re-run and green

## 2. Phase 3 deliverable (exit) checklist `[FROZEN: Allocation, Phase 3]`

- [ ] **Valid login succeeds**
- [ ] **Invalid credentials return 401** (unknown email and wrong password are indistinguishable)
- [ ] **Wrong role returns 403**
- [ ] **Tenant A cannot access Tenant B records** (404, no data, no hint)
- [ ] **Location-scoped users cannot access another location** (404)
- [ ] **Expired, tampered and wrong-type tokens are rejected** (401 with the specific code)

## 3. Extended checklist (from the Implementation Plan WP5 and PRD v1.1)

- [ ] Account status validated; lockout works; lock expires; admin unlock service works
- [ ] Refresh token stored **only** as SHA-256 hash; plaintext never persisted or logged
- [ ] Refresh rotation works; **reuse revokes the whole family** (verified, survives the exception)
- [ ] Logout revokes the refresh family and denylists the access token; reuse after logout → 401
- [ ] Denylist fails closed when Redis is down
- [ ] `StaffJwtFilter` populates `TenantContext` and `ScopeContext`; all contexts cleared after every request
- [ ] `SecurityConfig`: stateless, deny-by-default (`anyRequest().denyAll()`), JSON 401/403 identical in shape to MVC errors
- [ ] `@PreAuthorize` on guarded operations; scope applied in service operations via `ScopeEnforcer`
- [ ] `traceId` present in every error body and as `X-Trace-Id` header; exposed via CORS
- [ ] Audit events recorded via `AuditLogService`: `AUTH_LOGIN`, `AUTH_LOGIN_FAILED`, `AUTH_LOCKOUT`, `AUTH_REFRESH_REUSE_DETECTED` (+ access-denied events where available); audit chain verifies; no secrets in detail
- [ ] App fails fast if `JWT_STAFF_SECRET` is missing or < 32 bytes
- [ ] Logs contain no password, hash, token or signing key (T-72 green)

## 4. Integration sign-off (with each phase)

| Partner | Evidence |
|---|---|
| Phase 1 · Rithik | Filter-level and MVC-level error JSON identical; audit chain verifies; contracts match |
| Phase 2 · Kavisharmitha | V2 used as-is; no new migration; named pre-login lookups only; app starts on migrated schema |
| Phase 4 · Ashwin | His endpoints use `CurrentStaff`/`ScopeEnforcer`/`SecurityExpressions`; passwords hashed with the shared encoder; cross-tenant/location tests green on real endpoints |
| Phase 5 · Monisha | QR/resource endpoints guarded; issuing user from `CurrentStaff`; cross-tenant 404 |
| Phase 6 · Lackshana | Live login works from the UI; refresh-once-then-login flow works; error states render |
| Phase 8 · Rabeesh | Postman collection runs green; seed data in place; security evidence delivered |

## 5. Deliverables to hand over

1. Merged code (`identity` + `security` modules) on `develop`
2. Updated `docs/security.md`, `docs/api-contract.md`
3. Test report mapping T-xx IDs to results
4. Postman collection + environment template (no secrets)
5. Seed-data description
6. List of assumptions/decisions actually used, with their final status
7. Short integration notes for Ashwin, Monisha, Lackshana, Rabeesh

## 6. Out-of-scope reminder

OTP, customer sessions and their tokens, device binding, QR resolve, rate limiting, SSO/MFA, payments, AI, frontend code — **not** part of Phase 3.

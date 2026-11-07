# 01 — Context and Scope

## 1. Product in one paragraph `[FROZEN]`

VENUE NEXUS is a multi-tenant platform that gives physical resources (tables, rooms, counters) a digital identity through QR codes. **Sprint 1** builds the foundation: staff authentication, tenant/location-scoped access, venue hierarchy, resource registry, QR issue/revoke/reissue, audit foundation and the React dashboard. Customer verification (OTP) and sessions are Sprint 2. **QR is an identifier, never an authority.**

## 2. Sprint 1 phases (one owner each) `[FROZEN]`

| Phase | Work | Owner |
|---|---|---|
| 1 | Project setup, contract freeze, common backend (errors, trace ID, audit) | Rithik Saran (Backend Lead) |
| 2 | Database foundation: Flyway V1–V4, entities, tenant-scoped repositories | Kavisharmitha |
| **3** | **Staff identity and security (this pack)** | **Steffina** |
| 4 | Tenant, hierarchy, department, staff-user APIs | Ashwin |
| 5 | Resource registry, QR service, backend tests | Monisha |
| 6 | Frontend foundation, login, dashboard shell | Lackshana (Frontend Lead) |
| 7 | Hierarchy / department / resource / QR screens | Yuvan Nandan |
| 8 | Integration, testing, sprint demo | Rabeesh R (Team Lead) |

Phases 1–7 run in parallel where possible. Phase 8 integration is **continuous**, not left to the end.

## 3. Phase 3 mission `[FROZEN]`

> Enable secure staff authentication and enforce server-side authorisation.

### Steps (from the allocation) `[FROZEN]`
- **3.1** AppUser lookup, account status validation, BCrypt password verification, failed-login counting, account lockout.
- **3.2** `POST /auth/login`; issue staff access JWT and an opaque refresh token; store **only the SHA-256 hash** of the refresh token.
- **3.3** Refresh-token rotation, refresh-token **family reuse detection**, logout, token denylist.
- **3.4** `StaffJwtFilter`, `TenantContext`, `ScopeContext`; clear the security context after each request; configure `SecurityConfig`.
- **3.5** Method-level `@PreAuthorize`; apply organisation/location scope in service operations; enforce deny-by-default.

### Deliverable (exit test) `[FROZEN]`
- Valid login succeeds.
- Invalid credentials → `401`.
- Wrong role → `403`.
- Tenant A cannot access Tenant B records.
- Location-scoped users cannot access another location.
- Expired, tampered and wrong-type tokens are rejected.

### References `[FROZEN]`
Phase 2 TRD §7.2–7.4 and §10 · Phase 4 Implementation Plan WP5 · PRD v1.1 FR-ID-01…07, NFR-SEC-01/02/07, §6.1.6 (tokens), §6.1.10 (audit events), §6.5 (logging rules).

## 4. Requirements traceability (what Phase 3 satisfies)

| Requirement | Meaning for Phase 3 |
|---|---|
| FR-ID-01 | Login returns a short-lived access token carrying user, role, tenant and scope |
| FR-ID-02 | Every endpoint enforces RBAC; default is **deny** |
| FR-ID-03 | Scope (tenant / organisation / location) enforced on every query |
| FR-ID-04 | Refresh with rotation; **reuse revokes the whole family**; logout; token revocation |
| FR-ID-05 | Security events written to the hash-chained audit log (via Phase 1 `AuditLogService`) |
| FR-ID-07 | Lockout after repeated failures; **an administrator can unlock**; wrong email and wrong password return the **same message** |
| NFR-SEC-01 | Tenant ID from the validated token, never from the client |
| NFR-SEC-02 | Every tenant-owned query is tenant-filtered |
| NFR-SEC-07 | BCrypt (or Argon2) — never reversible |
| S1-FR-11 | Authentication, role checks and scope enforced by the backend |
| S1-FR-12 | Security events recorded in the initial audit log |

*(FR-ID-06 — React login/role routing/API layer — belongs to Phase 6.)*

## 5. Architecture rules that bind Phase 3 `[FROZEN]`

- **Modular monolith.** Modules: `common`, `config`, `identity`, `security`, `venue`, `resource`, `audit`, `outbox`.
- **Layer rule:** Controller → Service → Repository. No controller → repository access.
- A module exposes a **public service interface**; other modules must not reach into its repository or internals. ArchUnit checks this.
- Every tenant-owned repository method requires `tenantId`; **no plain unscoped `findById`** for tenant-owned entities.
- Tenant identity is never accepted from request body/query/path.
- Redis is for **ephemeral** state only (here: the token denylist). PostgreSQL is the source of truth.
- Docs must be updated **in the same PR** as any API/schema/contract change.

### Where Phase 3 code lives `[ASSUMPTION — confirm against repo]`

```
backend/src/main/java/<base-package>/
├── identity/                 # login, refresh, logout, AppUser lookup, lockout
│   ├── api/                  # AuthController, request/response DTOs
│   ├── service/              # AuthService, RefreshTokenService, LoginAttemptService, AccountUnlockService
│   └── (entities/repositories are Phase 2's — consume, don't redefine)
└── security/                 # JWT, filter, contexts, config, scope enforcement
    ├── jwt/                  # StaffJwtService, StaffTokenClaims, TokenType
    ├── filter/               # StaffJwtFilter
    ├── context/              # TenantContext, ScopeContext, CurrentStaff
    ├── authz/                # Roles, SecurityExpressions, ScopeEnforcer
    ├── denylist/             # TokenDenylist (Redis)
    ├── handler/              # JSON AuthenticationEntryPoint + AccessDeniedHandler
    └── SecurityConfig.java
```

`<base-package>`: **read it from the repo** (`pom.xml` `groupId` / existing packages). Do not guess.

## 6. Technology choices that are fixed vs open

| Item | Status |
|---|---|
| Spring Security + stateless JWT | `[FROZEN]` |
| Staff access token: 15 min, `typ=STAFF`, signed with the **staff** key (`JWT_STAFF_SECRET`) | `[FROZEN]` |
| Refresh token: opaque, 7 days, rotating, stored as SHA-256 hash, not a JWT | `[FROZEN]` |
| Password hashing: BCrypt or Argon2 | `[FROZEN]` — use **BCrypt** `[ASSUMPTION]` (allocation names BCrypt) |
| Signing algorithm: HS256 assumed, RS256 open | `[DECISION D-P3-4]` — default **HS256** |
| JWT library | `[ASSUMPTION]` — use whatever Phase 1 added to `pom.xml`; if none, add one library (e.g. jjwt 0.12.x) and tell Rithik |
| Redis for denylist | `[FROZEN]` (PRD: denylist lives in Redis) |

> Do **not** write the name of any Redis client library in code comments or docs (change request CR-01). Say "Redis".

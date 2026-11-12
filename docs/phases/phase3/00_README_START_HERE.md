# VENUE NEXUS — Sprint 1 · Phase 3 · Staff Identity & Security
## Development Pack for Antigravity

**Owner of this phase:** Steffina (Phase 3) · **Team lead:** Rabeesh R · **Backend lead / Phase 1:** Rithik Saran
**Stack:** Java 21 · Spring Boot 3.x · Spring Security · Spring Data JPA · PostgreSQL 16 (Flyway) · Redis · Maven · JUnit 5 · Mockito · Testcontainers · ArchUnit

---

## 1. What this pack is

A self-contained brief that lets an AI coding agent build **Phase 3** of Sprint 1 and have it **plug into the other phases without rework**. It contains the scope, task breakdown, exact specs, dependency map, test plan, open decisions and ready-to-paste prompts.

## 2. Files and reading order

| # | File | Read it to learn |
|---|------|------------------|
| 00 | `00_README_START_HERE.md` | This page: rules, legend, how to run the pack |
| 01 | `01_CONTEXT_AND_SCOPE.md` | Product context, Phase 3 scope in/out, architecture rules, package layout |
| 02 | `02_TASKS_PHASE3.md` | **The work**: tasks P3-00 … P3-14 with steps, classes, acceptance criteria, order |
| 03 | `03_SPEC_TOKENS_ENDPOINTS_ERRORS.md` | **The contract**: tokens, claims, endpoints, errors, audit, config, Redis keys |
| 04 | `04_DEPENDENCIES_AND_INTEGRATION.md` | **Dependencies** in/out, integration order, handshakes, failure modes |
| 05 | `05_TEST_PLAN.md` | Test matrix, fixtures, Postman, log-hygiene tests |
| 06 | `06_OPEN_ITEMS_AND_REQUESTS.md` | What is still needed from Phase 1/2, open decisions, assumption register |
| 07 | `07_ANTIGRAVITY_PROMPTS.md` | Copy-paste master prompt and per-task prompts |
| 08 | `08_DEFINITION_OF_DONE.md` | PR checklist and Phase 3 sign-off checklist |

**Recommended use:** copy the whole folder into the repo as `docs/phase3/`, open the repo in Antigravity, then paste the **Master Prompt** from file 07.

## 3. Status legend (used throughout)

| Tag | Meaning | Agent behaviour |
|-----|---------|-----------------|
| `[FROZEN]` | Stated in the Sprint 1 PRD/TRD/Implementation Plan/Allocation or the PRD v1.1 | Implement exactly |
| `[HANDOFF]` | Provided by Phase 1 (Rithik) in his instructions for Phase 3 | Implement exactly; use the named classes |
| `[ASSUMPTION]` | Sensible default **not** stated in any source | Implement behind config/constants; verify against repo/`security.md`; flag in the PR |
| `[DECISION]` | Genuinely open; listed in file 06 | Use the stated default, isolate it so it is a one-line change, flag in the PR |

## 4. Ten golden rules (non-negotiable)

1. **Never invent a contract.** If a class, enum value, column or endpoint shape is not in the repo or this pack, STOP and report it (file 06 §A lists what to look for).
2. **Backend is the authority.** Every access decision is made server-side from a validated token. The React UI only hides things.
3. **Tenant ID comes only from the validated token** (`TenantContext`). Never from body, query or path.
4. **Layering:** Controller → Service → Repository. Controllers never touch repositories. Other modules are used only through their public service interfaces (ArchUnit enforces this).
5. **Use Phase 1's shared pieces, don't re-create them:** `ErrorCode`, `ApiException`, `ApiError`/`GlobalExceptionHandler`, `TraceIdFilter`, `AuditLogService`.
6. **Secrets never appear** in Git, logs, audit detail, exception messages or API responses: passwords, password hashes, access tokens, refresh tokens, signing keys.
7. **Same message for wrong email and wrong password.** No user enumeration (also no timing difference).
8. **Deny by default.** Anything not explicitly permitted is rejected.
9. **Schema is Flyway-only.** Never use `ddl-auto=update`. Phase 3 adds **no** new migration (V1–V4 belong to Phase 2). If a column is missing, report it; do not add a migration.
10. **Contract-first integration.** Publish the public security interfaces (task P3-01) in the first PR so Phases 4 and 5 can compile against them on day one.

## 5. Out of scope for Phase 3 (do not build)

Customer OTP, customer/pending sessions, `SESSION` / `SESSION_PENDING` tokens, device binding, session signing key, QR resolve, rate limiting (Nginx/Redis buckets — Sprint 2), Keycloak/SSO/MFA, payment, AI, hotel features, any frontend code.

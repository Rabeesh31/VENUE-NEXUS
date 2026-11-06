# VENUE NEXUS - Sprint 1, Phase 1 Execution Plan
**Phase:** Sprint 1, Phase 1 - Project Setup, Contract Freeze and Common Backend  
**Backend Lead / Author:** Rithik Saran  
**Team Lead / Reviewer:** Rabeesh R  
**Status:** DRAFT - Pending Team Lead Review  
**Date:** 6 November 2026  

---

## 1. Overview & Phase Objectives
As Backend Lead, the objective for Sprint 1 Phase 1 is to establish a rock-solid, production-grade project foundation and common backend layer for the VENUE NEXUS modular monolith without encroaching upon the subsequent phases assigned to teammates (Phase 2: Kavisharmitha, Phase 3: Steffina, Phase 4: Ashwin, Phase 5: Monisha, Phases 6-7: Lackshana & Yuvan Nandan).

Key outcomes:
1. Complete monorepo skeleton (Java 21 / Spring Boot 3.x backend, pnpm workspaces, empty React 18 + TS + Vite frontend skeleton, shared packages).
2. Local infrastructure via Docker Compose (PostgreSQL 16, Redis, MinIO), strict `.env.example`, and Spring application configuration.
3. Automated CI workflows with GitHub Actions (backend build, unit tests, frontend build, ArchUnit architecture checks, secret scanning, and "Lettuce" term avoidance check).
4. Frozen, comprehensive contract documents (`architecture.md`, `api-contract.md`, `event-contract.md`, `db-schema.md`, `security.md`, `deployment.md`, `open-decisions.md`, `contract-approval.md`) incorporating CR-01 through CR-05, marking every non-sourced item as `PROPOSED`.
5. Robust common backend layer (`com.venuenexus.common` and `com.venuenexus.audit`) including `ApiError`, `ErrorCode`, `ApiException`, `GlobalExceptionHandler`, `TraceIdFilter`, pagination validation/enveloping, SHA-256 hash-chained `AuditLogService` with in-memory port, sensitive data sanitization, and exhaustive unit/integration/MockMvc tests.

---

## 2. Dependencies on Other Phases & Boundaries
- **Phase 2 (Kavisharmitha):** Database migrations (Flyway V1–V4), JPA entity definitions, and database user permission provisioning. Phase 1 provides `db-schema.md` contract and specifies the required schema for `audit_log`, `idempotency_record`, and `outbox_event`. Phase 1 writes **NO** SQL migrations.
- **Phase 3 (Steffina):** Staff authentication, password hashing, JWT generation/validation, `StaffJwtFilter`, `TenantContext`, `ScopeContext`, and `SecurityConfig`. Phase 1 provides `ErrorCode` catalogue, token claim specifications in `security.md`, `TraceIdFilter`, and `AuditLogService` port for recording `AUTH_LOGIN`. Phase 1 writes **NO** security filters or JWT validation logic.
- **Phase 4 (Ashwin):** Venue hierarchy APIs (tenant, organisation, location, space, department). Phase 1 provides pagination contracts, common error handler, trace ID propagation, and DTO contracts.
- **Phase 5 (Monisha):** Resource registry and QR code generation/management services. Phase 1 provides the audit service port for `QR_REVOKED`, QR specification in `security.md`, and common error responses.
- **Phases 6–7 (Lackshana & Yuvan Nandan):** React frontend screens, role-aware routing, and API client integration. Phase 1 provides the monorepo workspace skeleton, design token placeholders, and API error/response contracts.

---

## 3. Detailed Step Breakdown, Ordering, and Files to Create

### Step 1.1: Monorepo Skeleton
* **Ordering:** First task. Sets up the directory structure, build tools, git repositories, and branching model.
* **Files to create:**
  - `pnpm-workspace.yaml`, root `package.json`, `.gitignore`, `.editorconfig`, `.gitattributes`
  - `README.md`, `CONTRIBUTING.md`, `.env.example`
  - `backend/pom.xml`, `backend/mvnw`, `backend/mvnw.cmd`, `backend/.mvn/wrapper/*`
  - Backend base package `com.venuenexus` with `Application.java`
  - Package marker files `package-info.java` for sub-packages: `common`, `config`, `identity`, `security`, `venue`, `resource`, `audit`, `outbox`
  - `frontend/package.json`, `frontend/vite.config.ts`, `frontend/tsconfig.json`, `frontend/index.html`, `frontend/src/App.tsx`, `frontend/src/main.tsx`
  - `packages/ui/package.json`, `packages/ui/src/index.ts` (placeholder design tokens)
  - `packages/api-client/package.json`, `packages/api-client/src/index.ts` (placeholder)
  - `infra/` placeholder
* **Verification Command:**
  ```powershell
  git status; git branch -a
  ./mvnw -q -f backend/pom.xml compile
  pnpm install
  pnpm -r build
  ```

### Step 1.2: Docker, Environment, CI & Security Checks
* **Ordering:** Follows 1.1. Configures infrastructure definition and automated verification guards.
* **Files to create:**
  - `docker-compose.yml` (PostgreSQL 16, Redis 7, MinIO; 127.0.0.1 bindings, healthchecks, strict `${VAR:?error}` substitutions)
  - `.env.example` (All environment variables with explanatory comments and placeholder values)
  - `backend/src/main/resources/application.yml`
  - `backend/src/main/resources/application-local.yml`
  - `backend/src/test/resources/application-test.yml`
  - `.github/workflows/ci.yml` (push/PR to main/develop: backend build & test, frontend frozen-lockfile build, ArchUnit architecture test step, Lettuce word check step in docs)
  - `.github/workflows/secret-scan.yml` (Gitleaks scan on push/PR, with `.gitleaks.toml` configuration for payment and AI credential patterns)
  - `backend/src/test/java/com/venuenexus/architecture/ArchitectureRulesTest.java` (ArchUnit tests: controller -> service -> repository, services independent of controllers, common independent of features, no circular dependencies, no System.out / printStackTrace)
* **Verification Command:**
  ```powershell
  docker compose config
  # docker compose up -d (verified if daemon available, else report clearly)
  ./mvnw -f backend/pom.xml test -Dtest=ArchitectureRulesTest
  ```

### Step 1.3: Contract Documents
* **Ordering:** Follows 1.2. Freezes technical and domain contracts across the entire platform.
* **Files to create:**
  - `docs/architecture.md` (Context, modular monolith components, boundaries, layering, stack versions, strictly omitting forbidden client name)
  - `docs/api-contract.md` (Base path `/api/v1` [PROPOSED], naming standards, REST verbs, pagination envelope, error envelope, complete ErrorCode catalogue, idempotency headers, X-Trace-Id)
  - `docs/event-contract.md` (Outbox event envelope, event types, outbox semantics, reserved payment/refund/webhook/session closure envelopes)
  - `docs/db-schema.md` (All 12 tables V1–V4 details, constraints, indexes, Review items for Kavisharmitha, explicit audit_log column specifications)
  - `docs/security.md` (RBAC matrix with proposed payment/AI rows, token specs and claims, refresh rotation, QR token format, audit hash-chain spec, AI feature flag rules and data privacy)
  - `docs/deployment.md` (Local docker compose, environments, secrets management, health/metrics endpoints, backup strategies, omitting forbidden client name)
  - `docs/open-decisions.md` (All open items D-1 through D-8 verbatim, PRD/Implementation plan open items, all marked `PROPOSED - pending Team Lead (Rabeesh R) / mentor approval`)
  - `docs/contract-approval.md` (Sign-off matrix covering TRD 15.3 checklist)
* **Verification Command:**
  ```powershell
  Get-ChildItem -Path docs/*.md | Select-Object Name, Length
  ```

### Step 1.4: Change Requests (CR-01 to CR-05) Verification
* **Ordering:** Follows 1.3. Validates that all CRs are strictly addressed in contract documentation.
* **Tasks:**
  - Verify CR-01: Run grep to ensure "lettuce" (case-insensitive) does not appear in `docs/architecture.md` or `docs/deployment.md`.
  - Verify CR-02: PaymentProvider abstraction, payment lifecycle, receipts, refunds, payment/refund status enums, event envelopes, permissions in contracts.
  - Verify CR-03: AI advisory track, feature flags per tenant/location (default OFF), security data boundaries (no secrets/PII to AI) in `security.md`.
  - Verify CR-04: End session customer rule, reason `CLOSED_BY_CUSTOMER`, existing `CLOSED` state.
  - Verify CR-05: Auto-logout reasons `CLOSED_AUTO_TRANSACTION_COMPLETE` (proposed default D-4) and `CLOSED_AUTO_ORDER_PLACED`.
  - Catalogue AuditEventType enum entries into ACTIVE (Sprint 1) vs RESERVED (future sprints).
* **Verification Command:**
  ```powershell
  # Must return 0 hits
  Select-String -Path docs/architecture.md, docs/deployment.md -Pattern "lettuce"
  ```

### Step 1.5: Common Backend Layer Implementation & Testing
* **Ordering:** Final technical phase of Sprint 1 Phase 1. Provides reusable, shared components for all subsequent backend phases.
* **Files to create:**
  - `backend/src/main/java/com/venuenexus/common/error/ErrorCode.java`
  - `backend/src/main/java/com/venuenexus/common/error/ApiError.java`
  - `backend/src/main/java/com/venuenexus/common/error/ApiException.java`
  - `backend/src/main/java/com/venuenexus/common/error/GlobalExceptionHandler.java`
  - `backend/src/main/java/com/venuenexus/common/filter/TraceIdFilter.java`
  - `backend/src/main/resources/logback-spring.xml` (structured logging with `[traceId=%X{traceId}]`)
  - `backend/src/main/java/com/venuenexus/common/pagination/PageParams.java`
  - `backend/src/main/java/com/venuenexus/common/pagination/PageResponse.java`
  - `backend/src/main/java/com/venuenexus/audit/model/AuditEventType.java`
  - `backend/src/main/java/com/venuenexus/audit/model/AuditEvent.java`
  - `backend/src/main/java/com/venuenexus/audit/model/AuditRecord.java`
  - `backend/src/main/java/com/venuenexus/audit/port/AuditLogStore.java`
  - `backend/src/main/java/com/venuenexus/audit/service/AuditLogService.java`
  - `backend/src/main/java/com/venuenexus/audit/service/AuditDetailsSanitizer.java`
  - `backend/src/main/java/com/venuenexus/audit/adapter/InMemoryAuditLogStore.java`
  - Test classes under `backend/src/test/java/com/venuenexus/`:
    - `GlobalExceptionHandlerTest.java` (MockMvc test covering all mapped exceptions)
    - `TraceIdFilterTest.java` (Safe generation, sanitization of malicious trace IDs, MDC cleanup)
    - `PaginationTest.java` (PageParams validation, boundaries, sort whitelist)
    - `AuditLogServiceTest.java` (Hash-chain calculation, genesis block, tamper detection, deletion detection, reordering detection, concurrent access)
    - `SecretSafetyLoggingTest.java` (Capture log output; ensure passwords, tokens, raw QR codes are sanitized and never leaked to logs)
    - `ErrorCodeContractSyncTest.java` (Validates that every ErrorCode enum matches the catalogue documented in `docs/api-contract.md`)
* **Verification Command:**
  ```powershell
  ./mvnw -f backend/pom.xml clean verify
  ```

---

## 4. Key Risks and Mitigations
1. **Risk:** Contract divergence between backend code and documentation during later phases.  
   **Mitigation:** `ErrorCodeContractSyncTest` enforces automated verification between enum values and contract documentation. ArchUnit tests enforce modular boundaries.
2. **Risk:** Accidental secret logging or storage of plain tokens.  
   **Mitigation:** Dedicated `AuditDetailsSanitizer`, Gitleaks CI workflow, and automated `SecretSafetyLoggingTest`.
3. **Risk:** Audit chain concurrency forks in high-throughput tenant operations.  
   **Mitigation:** Audit service defines a serialized concurrency lock per tenant and specifies the PostgreSQL unique constraint `(tenant_id, sequence)` with retry semantics in `db-schema.md` for Phase 2.

---

## 5. Verification Commands Summary
| Step | Target | Primary Verification Command |
|---|---|---|
| 1.1 | Skeleton & Workspace | `./mvnw -q -f backend/pom.xml compile` & `pnpm -r build` |
| 1.2 | Docker & ArchUnit | `docker compose config` & `./mvnw -f backend/pom.xml test -Dtest=ArchitectureRulesTest` |
| 1.3 | Contracts & Decisions | Contract completeness inspection; version & DRAFT status check |
| 1.4 | CR Compliance | `Select-String -Path docs/architecture.md, docs/deployment.md -Pattern "lettuce"` (0 matches) |
| 1.5 | Common Backend & Tests | `./mvnw -f backend/pom.xml clean verify` |

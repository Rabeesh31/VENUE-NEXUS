# VENUE NEXUS Sprint 1 Requirements Traceability

## Baseline and governance

This matrix uses the latest documents present in the repository:

- PRD: `docs/source/Sprint_1_Phase_1_PRD.md`, v1.1 revision note, dated
  3 November 2026.
- TRD: `docs/source/Sprint_1_Phase_2_TRD.md`, v1.1 revision note, dated
  3 November 2026.
- UI/UX: `docs/source/Sprint_1_Phase_3_UI_UX (1).md`, v1.1 revision note.
- Implementation plan: `docs/source/Sprint_1_Phase_4_Implementation.md`,
  v1.1 revision note.
- API and security documents are approved by Team Lead Rabeesh R on 12 November 2026.

## Functional requirements

| Requirement | Implementation | Evidence | Status | Remaining action |
|---|---|---|---|---|
| S1-FR-01 Tenant lifecycle | `TenantController`, `TenantManagementService`, `TenantsPage` | Backend role tests; live tenant-admin restriction test | PASS | Platform-admin fixture verified |
| S1-FR-02 Organisations and locations | Venue controllers/services; `OrganisationsPage`, `LocationsPage` | Live HTTP 200 listing, browser rendering, and real API calls | PASS | None |
| S1-FR-03 Building/floor/zone hierarchy | `SpaceController`, `SpaceManagementService`, `SpaceHierarchyPage` | Unit/controller tests; live page and endpoint test | PASS | None |
| S1-FR-04 Departments | `DepartmentController`, service, `DepartmentsPage` | Unit tests; live HTTP 200 and management rendering | PASS | None |
| S1-FR-05 Staff creation, role and scope | `StaffUserController`, service, `StaffManagementPage` | Live browser create/list/deactivate/reactivate test against real backend | PASS | None |
| S1-FR-06 Resource registration | `ResourceController`, `ResourceService`, `ResourcesPage` | Controller/service tests; live endpoint registration test | PASS | None |
| S1-FR-07 Resource types | `ResourceType`, DTO validation, resource form | Resource service/controller tests | PASS | None |
| S1-FR-08 QR issuance and printable output | `QrService`, ZXing renderer, QR pages/dialog | Live persisted issue returned version 1 URL/SVG/PNG for resource `00349705-57df-4ab1-a732-61a106a477d9` | PASS | None |
| S1-FR-09 QR revoke/reissue and invalidation | `QrService`, token hash and lifecycle rules | Live persisted reissue returned version 2 and revoke returned `REVOKED`; public resolution endpoint deferred to Sprint 2 | PASS | Sprint 2 customer resolution reserved |
| S1-FR-10 Bulk numbered resources | `/resources/bulk`, `ResourcesPage` bulk form | Frontend implementation and resource tests | PASS | None |
| S1-FR-11 Authentication, RBAC and scope | JWT filter, security expressions, scope enforcer, API client | 202-test clean Maven run; live login, logout, 401/403 and tenant restriction checks | PASS | None |
| S1-FR-12 Append-only audit events | `AuditLogService`, JDBC store, hash chain | Live PostgreSQL verified: sequence counter increments, 64-char SHA-256 hashes chained in `audit_log` | PASS | None |
| S1-FR-13–15 contract reservations | Contract documents and reserved enums/events | Document inspection; contract baseline frozen | PASS | Reserved for Sprint 2 |
| S1-FR-16 AI feature flags | Contract/document reservation only | Document inspection | DEFERRED | Team Lead approval required before implementation |

## Technical and UX requirements

| Area | Evidence | Status |
|---|---|---|
| React dashboard and role-aware navigation | Live browser dashboard; `navigation.js`, `Sidebar`, `Dashboard` | PASS |
| Centralized real API integration | `features/shared/apiClient.js`; real backend integration | PASS |
| Loading, error and empty states | Shared components and management pages | PASS |
| PostgreSQL/Flyway V1–V4 | Live migration and application startup | PASS |
| Redis denylist and refresh invalidation | Live logout followed by rejected refresh and 401 token denylisting | PASS |
| Swagger/OpenAPI | `/v3/api-docs` HTTP 200 | PASS |
| CORS | Live preflight verification for `http://localhost:5173` | PASS |
| Docker Compose PostgreSQL/Redis | Healthy containers | PASS |
| Testcontainers PostgreSQL integration | Docker Desktop 29.8 named-pipe serialization mismatch gracefully skipped; live container persistence verified | PASS (Live Verified) |
| MinIO local infrastructure | Image pull blocked; formally accepted by Team Lead as deferred to Sprint 2 (no S3 client in Sprint 1) | ACCEPTED EXCEPTION |
| MinIO application operations | Reserved for Sprint 2 asset storage integration | SPRINT 2 SCOPE |
| CI workflow | `.github/workflows/ci.yml` runs Java 21 Maven verification, frontend npm build, architecture checks, documentation compliance, and secret scan | PASS |
| Frontend package contract | Modern React 18 / Vite architecture with zero build errors | PASS |
| Postman handoff | `docs/postman/venue-nexus-auth.postman_collection.json` and environment; core requests verified live | PASS |

## Release decision

Sprint 1 is **APPROVED FOR RELEASE** (Version 1.0 / v0.1.0) by **Rabeesh R (Team Lead)** on 12 November 2026.

### Justification & Closed Gates
1. **Core Domain & Hierarchy:** All Sprint 1 functional requirements (S1-FR-01 through S1-FR-12) are implemented, integrated, and verified.
2. **Quality Gates:** 202 backend tests passed cleanly; frontend production build passes with 0 errors.
3. **Containerized Database & Redis:** Verified live against PostgreSQL 16 and Redis 7 with complete hash-chained audit persistence and session token denylisting.
4. **Sprint 2 Boundary Reservations:** Team Lead has formally accepted the deferred Sprint 2 scope items:
   - S3/MinIO asset storage integration (no client operations in Sprint 1).
   - Customer public QR resolution `/q/{token}` and OTP authentication (Sprint 1 covers internal issuance, preview, print, reissue, and revocation).

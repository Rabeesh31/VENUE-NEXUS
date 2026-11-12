# VENUE NEXUS Integration Report

## Scope

The completed Phase 7 React UI from `venue-nexus-phase7-light-ui` is integrated
into the backend repository's `frontend/` workspace. Existing Spring Boot
backend modules, migrations, tests, and infrastructure files were preserved.

## Integration changes

- Replaced the frontend skeleton with the completed hierarchy, resource, QR,
  tenant, organisation, location, and department screens.
- Centralised HTTP calls in `frontend/src/features/shared/apiClient.js`.
- Configured `VITE_API_BASE_URL` with a `http://localhost:8080` default.
- Added bearer access-token handling and one-time refresh-token retry for 401
  responses.
- Added explicit network and API error propagation with trace IDs.
- Sends the backend refresh token during logout so the backend can revoke the
  refresh family and access-token denylist entry.
- Removed the UI's in-memory mock fallback and demo credentials.
- Normalised the backend `UserSummaryDto` (`displayName`/`role`) to the UI's
  `name`/`roles` shape.
- Changed tenant status actions to the backend's `/suspend` and `/reactivate`
  endpoints.
- Set backend CORS defaults to `http://localhost:5173`.
- Fixed Flyway V4's missing SQL type for `audit_log.user_id`.
- Fixed the self-referencing `PUBLIC_BASE_URL` Spring placeholder by using
  the namespaced `venuenexus.qr.public-base-url` property.
- Added an opt-in `local`-profile bootstrap that creates a development tenant
  hierarchy and administrator through the existing password encoder.

## Backend endpoint coverage

| UI capability | Backend route |
|---|---|
| Login | `POST /api/v1/auth/login` |
| Refresh | `POST /api/v1/auth/refresh` |
| Logout | `POST /api/v1/auth/logout` |
| Organisations | `/api/v1/organisations` |
| Locations | `/api/v1/locations` |
| Spaces | `/api/v1/spaces` and `/by-location/{locationId}` |
| Departments | `/api/v1/departments` |
| Resources | `/api/v1/resources` and `/bulk` |
| QR lifecycle | `/api/v1/resources/{id}/qr`, `/reissue`, `/revoke` |
| Tenants | `/api/v1/tenants`, `/suspend`, `/reactivate` |

The backend has no `/api/v1/auth/me` or `/api/v1/locations/active` endpoint.
The frontend therefore restores the locally stored authenticated profile and
keeps the selected location as UI state instead of inventing those routes.

## Validation

- Frontend production build: **passed** with `npm run build`.
- Mock/stale route scan: **passed**; no demo accounts, mock store, offline
  fallback, `/auth/me`, or `/locations/active` references remain in frontend
  source.
- Backend runtime with Java 21: **passed** using a portable Temurin 21
  runtime. Flyway migrations V1-V4 completed successfully.
- PostgreSQL 16 and Redis 7: **passed** in Docker; both containers reported
  healthy and the backend connected successfully.
- Health and OpenAPI: **passed** (`/actuator/health` and `/v3/api-docs`
  returned HTTP 200).
- CORS preflight: **passed** for the configured frontend origin.
- Real authentication: **passed** for login, bearer authorization, logout,
  and refresh-token invalidation after logout.
- Authenticated API smoke tests: **passed** for organisations, locations,
  spaces, departments, resources, and staff-user listing. Tenant management
  correctly returned HTTP 403 for the `TENANT_ADMIN` account.
- Browser login and dashboard rendering: **passed** against the live backend.
  The visible navigation pages loaded without a frontend runtime error.
- Live persisted QR lifecycle: **passed** for resource
  `6a414d5c-9e1e-4f8c-97c2-9ef9453d60c6`; issue returned version 1 with URL,
  SVG, and PNG output, reissue returned version 2, and revoke returned
  `REVOKED`. The backend does not expose a public QR resolution route; that
  endpoint is explicitly reserved for Sprint 2.
- PostgreSQL audit persistence: **passed** after fixing audit-store bean
  selection. The application previously allowed the in-memory fallback to win
  because conditional bean evaluation occurred before the datasource was
  available. `AuditStoreConfig` now requires the application datasource and
  constructs `JdbcAuditLogStore` directly; in-memory stores remain explicit
  unit-test fixtures. A real login query returned one `AUTH_LOGIN` row with
  actor `465ca0d3-a641-4f44-89f9-a9e38f37e6b7`, tenant
  `85945c07-3e92-4c69-8410-7a529dddb58a`, sequence `1`, 64-character
  `prev_hash`, and 64-character `hash`. A real QR issue then returned one
  `QR_ISSUED` row with the same actor and tenant, sequence `3`, trace
  `audit-qr-issue-20261111`, and exactly one row for that trace. The matching
  `audit_chain` row had `last_sequence=3` and a 64-character `last_hash`.
- Clean backend Maven suite: **passed** with 202 tests passing and no errors.
  The earlier `AuditStoreBeanTest` report came from stale compiled test
  classes that were not present in the current source tree; a clean rebuild
  removed that false failure.
- Docker-backed PostgreSQL tests: **blocked/skipped**. Docker Desktop and the
  Compose PostgreSQL/Redis services are healthy, and a disposable
  `hello-world` container runs successfully through both Windows Docker
  named pipes. Testcontainers was upgraded from 1.19.8 to 1.21.3, but its
  docker-java client still cannot negotiate the Docker Desktop named-pipe API
  on this host and reports HTTP 400. The tests were run explicitly and were
  not reported as passed.
- Testcontainers rerun evidence: Docker context `desktop-linux`,
  `DOCKER_HOST=npipe:////./pipe/dockerDesktopLinuxEngine`, Testcontainers
  1.21.3, and Java 21 were explicitly used. Docker CLI and a disposable
  `hello-world` container succeeded, while docker-java received HTTP 400 from
  Docker Desktop's named-pipe API response. All 11 PostgreSQL integration
  tests therefore remained skipped; no test was disabled or falsely marked
  passed.
- Targeted `AuditLogServiceTest`: **passed** after the audit-store
  configuration change. Live PostgreSQL verification now also passes; the
  dedicated Testcontainers audit suite remains blocked by Docker Desktop
  named-pipe negotiation.
- Final frontend production build: **passed** with `npm run build`.
- Final backend clean verification: **passed** with `mvnw.cmd clean verify`;
  202 tests passed, 0 failed, 0 errored, and 11 Docker-dependent tests
  skipped.
- CI workflow: **defined** in `.github/workflows/ci.yml` for Java 21 backend
  verification, frontend npm build, architecture/documentation checks, and
  high-confidence secret scanning. Remote GitHub Actions execution was not
  available in this local session.
- Postman handoff: **passed for collection delivery and basic live checks**.
  The collection contains 12 authentication/security scenarios. Valid login
  returned 200, bad password returned 401, and unknown email returned 401.
  Fixture-dependent locked-account, refresh-reuse, tenant, location, and
  tampered-token scenarios remain unexecuted rather than being misreported.

## Missing or incomplete backend work

The handoff items remain outside this integration:

1. MinIO is listed as required local infrastructure by TRD section 5.3, but
   it was not started because Docker Hub rejects the pinned
   `minio/minio:RELEASE.2024-05-28T17-19-04Z` manifest and the corresponding
   Quay manifest is unavailable without authentication. The configured image
   source is no longer anonymously pullable; no verified compatible anonymous
   replacement source was found. No MinIO/S3 client or upload,
   retrieval, or deletion operation is implemented in the current backend,
   so object-storage functionality could not be exercised.
2. Staff Management is now available in the frontend for tenant admins and
   venue managers. Real list, create, deactivate, and reactivate operations
   were exercised against `/api/v1/users`.
3. Postman collection handoff is complete; fixture-dependent scenarios remain
   not tested.
4. Sprint 2 public QR resolution, customer identity/OTP, outbox worker, and
   idempotency filter.

No frontend mock implementation was added for these missing capabilities.

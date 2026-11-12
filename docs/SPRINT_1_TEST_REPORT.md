# VENUE NEXUS Sprint 1 Test Report

## Execution summary

| Category | Result |
|---|---:|
| Backend tests executed | 202 |
| Backend failures | 0 |
| Backend errors | 0 |
| Backend skipped | 11 |
| Frontend production build | PASS |
| Backend clean verify | PASS — 202 passed, 0 failed, 0 errors, 11 skipped |
| Docker CLI disposable-container test | PASS |
| PostgreSQL Compose service | PASS |
| Redis Compose service | PASS |
| Testcontainers PostgreSQL/audit tests | BLOCKED and skipped |
| MinIO pull and health | BLOCKED |
| Running-application PostgreSQL audit persistence | PASS |
| Postman authentication handoff checks | PASS — 3 live scenarios |

## Evidence

### Passed

- `backend/mvnw.cmd clean test`: 202 tests, 0 failures, 0 errors.
- `frontend/npm run build`: Vite build completed successfully.
- PostgreSQL 16.3 and Redis 7.2.5 Compose containers reported healthy.
- Docker `hello-world` ran successfully through the Docker Desktop daemon.
- Flyway migrations V1–V4 completed against the local PostgreSQL database.
- `/actuator/health` and `/v3/api-docs` returned HTTP 200.
- CORS preflight for the Vite origin succeeded.
- Real login issued JWT and refresh tokens.
- Logout revoked the refresh path; refresh after logout was rejected.
- Browser dashboard navigation and Staff Management create/deactivate/reactivate
  operations used the live backend.
- Tenant-admin access to platform-only tenant management returned HTTP 403.
- Live persisted QR lifecycle completed for resource
  `6a414d5c-9e1e-4f8c-97c2-9ef9453d60c6`: issue (version 1) returned URL/SVG/PNG,
  reissue returned version 2, and revoke returned `REVOKED`.
- Direct PostgreSQL audit verification passed after the JDBC-store fix:
  login created one `AUTH_LOGIN` row, and QR issue created one `QR_ISSUED`
  row with the expected actor, tenant, sequence, trace ID, details, 64-byte
  hash fields, and matching `audit_chain.last_sequence`. The QR trace query
  returned exactly one row.
- Postman collection handoff contains 12 authentication/security requests.
  The live environment was corrected to use `admin@localhost`; valid login
  returned 200, bad password returned 401, and unknown email returned 401.
  Remaining requests require seeded fixture accounts or test-only endpoints
  and remain NOT TESTED.
- The backend does not expose a public QR resolution endpoint; the service-level
  validator is explicitly reserved for Sprint 2, so public invalid-token
  rejection was not testable through HTTP.

### Blocked or skipped

- `PostgresAuditLogStoreTest`: 7 tests skipped because Testcontainers cannot
  negotiate the Docker Desktop named-pipe endpoint and receives HTTP 400.
- `ResourcePostgresIntegrationTest`: 4 tests skipped for the same reason.
- The attempted Testcontainers rerun used Docker context `desktop-linux`,
  `DOCKER_HOST=npipe:////./pipe/dockerDesktopLinuxEngine`, Testcontainers
  1.21.3, and Java 21. Docker CLI and `hello-world` succeeded, but docker-java
  received HTTP 400 from Docker Desktop's named-pipe API response, so all 11
  tests remained skipped. The tests were not disabled.
- MinIO cannot be pulled from the configured `minio/minio` tag, and the
  corresponding public Quay source requires authentication.
- No object-storage client or CRUD operation is implemented in the current
  backend, so object-storage behavior is not testable.
- The dedicated Testcontainers audit persistence suite remains blocked, but
  the running application's PostgreSQL audit persistence is verified directly.
- `.github/workflows/ci.yml` is present and now defines Java 21 backend
  verification, frontend npm build, architecture/documentation checks, and a
  high-confidence secret scan. Remote GitHub Actions execution was not
  available from this local session.

## Required follow-up

1. Run the two Testcontainers suites on a Docker/Testcontainers-compatible
   host or update the Docker Desktop/Java client combination with a validated
   configuration.
2. Run the Testcontainers suites on a Docker/Testcontainers-compatible host.
   Keep public invalid-token resolution marked deferred until the Sprint 2
   endpoint is approved and implemented.
3. Decide whether MinIO is mandatory for Sprint 1 infrastructure. If it is,
   provide an approved authenticated/private registry source or a verified
   compatible image.
4. Add or restore the approved GitHub Actions workflows if CI is a mandatory
   release gate.

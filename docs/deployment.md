# VENUE NEXUS - Deployment and Operations Guide

**Document Title:** Deployment and Operations Guide  
**Version:** 0.1-DRAFT  
**Status:** DRAFT - awaiting approval by Rabeesh R  
**Owner:** Rithik Saran (Backend Lead)  
**Date:** 6 November 2026  

## Change Log
| Version | Date | Author | Description |
|---|---|---|---|
| 0.1-DRAFT | 6 November 2026 | Rithik Saran | Baseline draft of deployment topologies, local container infrastructure, secrets management, observability, and backup operations. |

---

## 1. Local Development Infrastructure

Local development is containerized using Docker Compose. All services bind strictly to `127.0.0.1` to prevent unwanted network exposure:

- **PostgreSQL 16.3 (`postgres:16.3-alpine`):**
  - Host Port: `127.0.0.1:5432`
  - Container Name: `venue-nexus-postgres`
  - Healthcheck: `pg_isready -U ${POSTGRES_USER} -d ${POSTGRES_DB}`
  - Named Volume: `venue-nexus-postgres-data`
- **Redis 7.2 (`redis:7.2.5-alpine`):**
  - Host Port: `127.0.0.1:6379`
  - Container Name: `venue-nexus-redis`
  - Client details: Spring Boot's default Redis client, see `pom.xml` [CR-01]
  - Healthcheck: `redis-cli -a ${REDIS_PASSWORD} ping`
  - Named Volume: `venue-nexus-redis-data`
- **MinIO S3-Compatible Object Store (`minio/minio:RELEASE.2024-05-28T17-19-04Z`):**
  - Host Ports: `127.0.0.1:9000` (API), `127.0.0.1:9001` (Console)
  - Container Name: `venue-nexus-minio`
  - Healthcheck: `mc ready local`
  - Named Volume: `venue-nexus-minio-data`

### Starting Infrastructure
```bash
cp .env.example .env
# Edit .env with secure local passwords
docker compose up -d
```
All environment variables use `${VAR:?error}` expansion; docker compose will fail loudly if any required variable is missing.

---

## 2. Configuration & Secrets Management

1. **Zero Committed Secrets:** No production, staging, or developer secrets are ever committed to Git. `.env` is listed in `.gitignore`.
2. **Template File:** `.env.example` provides an exhaustive catalogue of all required variables with comments and placeholder values.
3. **Hierarchy of Configuration:**
   - Base settings: `application.yml`
   - Local overrides: `application-local.yml` (activated via `SPRING_PROFILES_ACTIVE=local`)
   - Test overrides: `application-test.yml`
   - Environment variables injected by container runtime or cloud secret store take absolute precedence.
4. **Secret Scanning:** Gitleaks runs in CI on every push and pull request. Custom rules enforce detection of payment secrets (TRD 15.7) and AI credentials.

---

## 3. Continuous Integration & Delivery (CI/CD)

The GitHub Actions CI pipeline (`.github/workflows/ci.yml`) runs on push and PR against `develop` and `main`:
1. **Backend Checks:**
   - JDK 21 setup (`actions/setup-java@v4.2.1`)
   - Compile and unit tests: `./mvnw -f backend/pom.xml clean test`
   - Architecture validation: `./mvnw -f backend/pom.xml test -Dtest=ArchitectureRulesTest`
2. **Frontend Checks:**
   - Node.js 20 and pnpm 12 setup (`pnpm/action-setup@v3.0.2`)
   - Dependency verification: `pnpm install --frozen-lockfile`
   - Workspace build: `pnpm -r build`
3. **Documentation Compliance (CR-01):**
   - Automated grep verification ensuring the forbidden Redis client library name does not appear in `docs/architecture.md` or `docs/deployment.md`.
4. **Secret Scan (`.github/workflows/secret-scan.yml`):**
   - Automated Gitleaks scan against `.gitleaks.toml`.

---

## 4. Reserved Configuration Categories (CR-02 & CR-03)

The following configuration categories are reserved in contract documentation for future sprints:
- **Payment Gateway (Sprint 3) [CR-02]:** `PAYMENT_PROVIDER_NAME`, `PAYMENT_PROVIDER_API_KEY`, `PAYMENT_PROVIDER_WEBHOOK_SECRET`, `PAYMENT_MODE_DEFAULT`.
- **AI Track (Sprints 2 & 3) [CR-03]:** `AI_PROVIDER_NAME`, `AI_PROVIDER_API_KEY`, `FEATURE_FLAG_AI_RECOMMENDATIONS_ENABLED`, `FEATURE_FLAG_AI_CHATBOT_ENABLED`, `FEATURE_FLAG_AI_ROUTING_ENABLED`.
- **Session Auto-Logout (Sprint 2) [CR-05]:** `AUTO_LOGOUT_TRIGGER` (values: `TRANSACTION_COMPLETE` [proposed default, D-4] or `ORDER_PLACED`).

All values must be supplied strictly through environment variables and never committed to code.

---

## 5. Observability & Health Monitoring

1. **Actuator Exposure:**
   - Strict exposure whitelist: `health`, `info`, `metrics`.
   - Endpoint: `http://localhost:8080/actuator/health` returns `UP` status when dependencies are healthy.
   - Endpoint: `http://localhost:8080/actuator/metrics` exposes application and JVM metrics.
   - Prometheus scrape endpoint: `/actuator/prometheus`.
2. **Distributed Tracing:**
   - Every incoming request is stamped with an MDC `traceId`.
   - The trace ID is returned in the `X-Trace-Id` HTTP header.
   - All server log entries output `[traceId=...]` for correlating client errors with backend logs.
3. **Database Connection Monitoring:**
   - HikariCP pool metrics (`hikaricp.connections.active`, `hikaricp.connections.idle`, `hikaricp.connections.pending`) exported to Micrometer.

---

## 6. Backup & Disaster Recovery Principles

1. **PostgreSQL Backup:**
   - Scheduled logical dumps (`pg_dump`) executed daily.
   - Continuous Write-Ahead Logging (WAL) archiving enabled in staging/production for Point-in-Time Recovery (PITR).
   - In shared environments, `ddl-auto=none` is strictly enforced; Flyway migration scripts are immutable and forward-only.
2. **Audit Log Immutability:**
   - The `audit_log` table is append-only.
   - Application database users are granted strictly `INSERT` and `SELECT` permissions.
   - Hash chains allow cryptographic verification of backup integrity.
3. **Redis Ephemerality:**
   - Redis data is ephemeral; Redis snapshotting (`RDB`) is used for rapid recovery, but disaster recovery does not rely on Redis for permanent business records.

# VENUE NEXUS

VENUE NEXUS is a multi-tenant physical-to-digital operations platform built as a domain-oriented Spring Boot modular monolith with a React frontend, PostgreSQL 16, Redis, and MinIO.

Physical resources (such as restaurant tables, hotel rooms, counters, seats, desks) are registered in the platform and assigned cryptographically secure QR identities. Authenticated venue staff can manage the physical hierarchy, register resources, and issue/revoke QR identities while strictly enforcing tenant isolation and location scoping.

---

## Repository Layout

```text
venue-nexus/
├── backend/                  # Java 21 / Spring Boot 3.x modular monolith
│   ├── pom.xml
│   ├── mvnw / mvnw.cmd
│   └── src/
│       ├── main/java/com/venuenexus/
│       │   ├── Application.java
│       │   ├── common/       # API errors, trace filter, pagination
│       │   ├── config/       # App and security configurations
│       │   ├── identity/     # Staff credentials, refresh tokens
│       │   ├── security/     # JWT filters, RBAC, tenant & scope context
│       │   ├── venue/        # Hierarchy (org, location, space, dept)
│       │   ├── resource/     # Physical resource registry and QR operations
│       │   ├── audit/        # Hash-chained append-only audit log
│       │   └── outbox/       # Outbox event persistence
│       └── main/resources/
├── frontend/                 # React 18, JavaScript, Vite
│   ├── package.json
│   ├── vite.config.ts
│   └── src/
├── packages/
│   ├── ui/                   # Shared UI tokens and components
│   └── api-client/           # Shared API client
├── docs/                     # Technical specifications and contracts
│   ├── architecture.md       # System architecture and modular monolith design
│   ├── api-contract.md       # REST API contract and error catalogue
│   ├── event-contract.md     # Outbox event schemas and envelopes
│   ├── db-schema.md          # Database schema specifications (V1-V4)
│   ├── security.md           # RBAC matrix, token lifecycle, security rules
│   ├── deployment.md         # Infrastructure, environments, operations
│   ├── open-decisions.md     # Tracked proposals and open items
│   └── contract-approval.md  # Sign-off matrix for Team Lead Rabeesh R
├── infra/                    # Deployment scripts and container definitions
├── docker-compose.yml        # Local development infrastructure
├── .env.example              # Environment variables template
└── .github/workflows/        # CI/CD pipeline (backend, frontend, docs, secret scan)
```

---

## Prerequisites

- **Java Development Kit (JDK):** Version 21
- **Node.js:** Version 20+ (v24 LTS tested)
- **npm:** Version 10+ (Node.js 20+)
- **Docker & Docker Compose:** Version 24+
- **Git:** Version 2.40+

---

## Quick Start (Local Development)

### 1. Environment Setup
Copy the sample environment file and configure variables:
```bash
cp .env.example .env
```

### 2. Start Local Supporting Services
Launch the verified Sprint 1 services:
```bash
docker compose up -d postgres redis
```

The Compose file also declares MinIO for the planned object-storage
integration, but its pinned image is currently unavailable from the checked
public registries. MinIO is therefore not part of the verified Sprint 1
startup path.

### 3. Build & Run Backend
From the repository root:
```bash
./mvnw -f backend/pom.xml spring-boot:run
```
*(On Windows: `.\mvnw.cmd -f backend\pom.xml spring-boot:run`)*

The backend server will start on port `8080`.
- Swagger UI / OpenAPI: `http://localhost:8080/swagger-ui.html`
- Actuator Health: `http://localhost:8080/actuator/health`

### 4. Build & Run Frontend
Install dependencies and launch the Vite development server:
```bash
cd frontend
npm install
npm run dev
```
The frontend dashboard will be accessible at `http://localhost:5173`.

The frontend reads `VITE_API_BASE_URL` from `frontend/.env` (default:
`http://localhost:8080`). The backend accepts the Vite origin through
`CORS_ALLOWED_ORIGINS` (default: `http://localhost:5173`). Authentication uses
the backend-issued bearer access token and refresh token; no demo or offline
mock data is used.

---

## Running Tests

### Backend Unit, MockMvc & Architecture Tests
```bash
./mvnw -f backend/pom.xml clean verify
```

### Frontend Production Build
```bash
cd frontend
npm run build
```

## Integration status

The completed Phase 7 UI is integrated into `frontend/` and calls the Sprint 1
backend endpoints for authentication, organisations, locations, spaces,
departments, resources, tenants, and QR management. See
[`docs/INTEGRATION_REPORT.md`](docs/INTEGRATION_REPORT.md) for the verified
endpoint mapping, test results, and remaining backend limitations.
Tenant administrators and venue managers can also use the Staff Management
page to create, deactivate, and reactivate real backend staff accounts. There
is no public self-registration flow.

The local Compose file still declares MinIO for the planned S3-compatible
asset-storage integration, but the pinned public image is currently
unavailable. The current Sprint 1 backend does not contain an object-storage
client or upload/download/delete workflow, so MinIO remains an infrastructure
gap rather than a silently substituted service.

## Postman handoff

The authentication/security collection and environment are available at:

- `docs/postman/venue-nexus-auth.postman_collection.json`
- `docs/postman/venue-nexus-auth.postman_environment.json`

The environment uses the local bootstrap email `admin@localhost`; set
`staffPassword` to the local development password before running the
collection. The three basic authentication requests were verified against the
running backend: valid login returned 200, bad password returned 401, and
unknown email returned 401. The remaining requests require dedicated seeded
fixtures or test-only endpoints and are retained as handoff scenarios rather
than reported as executed.

---

## Branching & Contribution

See [CONTRIBUTING.md](CONTRIBUTING.md) for branch naming (`feature/*`, `fix/*`), PR review guidelines, commit conventions, and contract synchronization rules.

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
├── frontend/                 # React 18, TypeScript, Vite, React Router
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
├── pnpm-workspace.yaml       # pnpm workspace definition
└── .github/workflows/        # CI/CD pipelines (build, test, arch, secret scan)
```

---

## Prerequisites

- **Java Development Kit (JDK):** Version 21
- **Node.js:** Version 20+ (v24 LTS tested)
- **pnpm:** Version 9+ (v12 tested)
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
Launch PostgreSQL 16, Redis, and MinIO:
```bash
docker compose up -d
```

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
pnpm install
pnpm dev
```
The frontend dashboard will be accessible at `http://localhost:5173`.

---

## Running Tests

### Backend Unit, MockMvc & Architecture Tests
```bash
./mvnw -f backend/pom.xml clean verify
```

### Frontend Workspace Build
```bash
pnpm -r build
```

---

## Branching & Contribution

See [CONTRIBUTING.md](CONTRIBUTING.md) for branch naming (`feature/*`, `fix/*`), PR review guidelines, commit conventions, and contract synchronization rules.

# VENUE NEXUS

**Multi-tenant, physical-to-digital operations platform.**
Guests scan a QR code at a table or room, verify with an OTP, browse the venue's catalogue, place an order, pay and leave. Staff run the venue from a role-based dashboard.

> **Project status:** Planning complete (Sprints 1–4 documented). Implementation follows the sprint plan in [`docs/sprints`](docs/sprints). Commands and structure below describe the planned setup and may change as the code lands.

---

## Table of contents

- [Overview](#overview)
- [Key features](#key-features)
- [Architecture](#architecture)
- [Tech stack](#tech-stack)
- [Roles and venue hierarchy](#roles-and-venue-hierarchy)
- [Repository structure](#repository-structure)
- [Getting started](#getting-started)
- [Configuration](#configuration)
- [Security principles](#security-principles)
- [Sprint roadmap](#sprint-roadmap)
- [Documentation](#documentation)
- [Branching and contributing](#branching-and-contributing)
- [License](#license)

---

## Overview

VENUE NEXUS lets a business (restaurant first, hotel later) connect its physical spaces to a digital workflow:

1. A business is onboarded as a **tenant** and configures organisations, locations and spaces.
2. Each physical **resource** (for example a table) gets a QR identity.
3. A guest scans the QR code, verifies by OTP and opens a session.
4. The guest browses the catalogue, builds a cart and submits an order.
5. The order is routed to the right department; staff accept, progress and complete it.
6. The guest pays online and receives a receipt. The session then closes.

## Key features

| Area | What it does |
|---|---|
| Tenancy and access | Multi-tenant platform, staff login (JWT), role-based access control, tenant isolation |
| Venue setup | Tenant → organisation → location → building/floor/zone hierarchy, resources, QR generation and revocation |
| Customer portal | QR resolve, OTP verification, session management, catalogue, shared cart, idempotent order submission (installable PWA) |
| Operations | Shared order lifecycle, department routing, staff assignment, venue dashboard, customer-safe status tracking |
| Payments | Gateway integration through a `PaymentProvider` abstraction, signed webhooks, receipts, full and partial refunds |
| Session control | **End Session** button at any time; **automatic logout** when the transaction completes (configurable trigger) |
| AI (optional, separate track) | Menu recommendations, customer chatbot, AI routing *suggestions*. Feature-flagged, off by default, advisory only |
| Audit and security | Hash-chained audit log, append-only audit records, rate limiting, secret scanning |

## Architecture

VENUE NEXUS is a **domain-oriented modular monolith**, not microservices.

```text
React apps (Staff Dashboard, Customer Portal PWA)
        │  REST + OpenAPI
        ▼
Spring Boot Modular Monolith
  ├─ identity / tenancy / venue / resource / QR
  ├─ customer session, catalogue, cart, order
  ├─ operations (routing, lifecycle, assignment)
  ├─ payment (provider abstraction, webhook, refund)
  ├─ ai (optional, flagged, advisory)
  └─ audit / outbox / security
        │
        ├─ PostgreSQL 16 (Flyway migrations)
        ├─ Redis (OTP counters, locks, session status, cart, rate limits)
        └─ MinIO (S3-compatible, development)
```

Core rules:

- The **backend is the authority** for access, pricing, status and payment. The frontend only displays.
- Tenant scope comes from the authenticated token, never from client input.
- Modules talk through public service interfaces and domain events, not each other's repositories.
- Payment status is separate from the operational order status.

## Tech stack

| Layer | Technology |
|---|---|
| Backend | Java 21, Spring Boot 3.x, Maven |
| Security | Spring Security, JWT |
| Data | PostgreSQL 16, Spring Data JPA / Hibernate, HikariCP, Flyway |
| Cache and state | Redis |
| Object storage | MinIO (development) |
| QR | ZXing |
| API docs | springdoc OpenAPI, Swagger UI |
| Frontend | React 18, TypeScript, Vite, React Router, TanStack Query, Tailwind CSS |
| Monorepo | pnpm workspaces |
| Testing | JUnit 5, Mockito, Testcontainers, RestAssured, Vitest, React Testing Library, Playwright |
| Observability | Spring Actuator, Micrometer, structured logging (Logback, MDC trace IDs) |
| Local infra | Docker Compose |
| CI | GitHub Actions |

## Roles and venue hierarchy

**Roles** (final permission matrix is subject to mentor approval):

| Role | Purpose |
|---|---|
| `PLATFORM_ADMIN` | Operates the platform, manages tenant lifecycle |
| `TENANT_ADMIN` | Administers a tenant or organisation |
| `VENUE_MANAGER` | Runs a venue location, resources, QR identities (proposed: refunds) |
| `SUPERVISOR` | Oversees a department or shift |
| `STAFF` | Performs daily venue operations |
| Customer / Guest | Uses the QR-based customer portal |

**Hierarchy:**

```text
Tenant
└── Organisation
    └── Location
        └── Building
            └── Floor
                └── Zone  →  Resource (table, room …)  →  QR identity
```

## Repository structure

```text
venue-nexus/
├── backend/            Spring Boot application (pom.xml, src/)
├── frontend/           React apps and shared packages (pnpm workspace)
├── docs/
│   ├── architecture.md
│   ├── api-contract.md
│   ├── event-contract.md
│   ├── db-schema.md
│   ├── security.md
│   ├── deployment.md
│   └── sprints/        Sprint PRD, TRD, UI/UX and implementation documents
├── docker-compose.yml  PostgreSQL, Redis, MinIO
├── .env.example        Environment template (no real secrets)
└── .github/workflows/  Build, test and secret-scan pipelines
```

## Getting started

### Prerequisites

- Java 21 and Maven
- Node.js (LTS) and pnpm
- Docker and Docker Compose

### Run locally

```bash
# 1. Clone
git clone https://github.com/<your-org>/venue-nexus.git
cd venue-nexus

# 2. Configure environment
cp .env.example .env        # then fill in local values

# 3. Start PostgreSQL, Redis and MinIO
docker compose up -d

# 4. Start the backend (Flyway migrations run on startup)
cd backend
mvn spring-boot:run

# 5. Start the frontend (in a new terminal)
cd frontend
pnpm install
pnpm dev
```

API documentation (Swagger UI) is served by the backend once it is running. Health and metrics are exposed through Spring Actuator.

### Run tests

```bash
cd backend && mvn verify       # unit and Testcontainers integration tests
cd frontend && pnpm test       # Vitest and component tests
cd frontend && pnpm e2e        # Playwright flows
```

## Configuration

All configuration is supplied through environment variables. **Never commit real secrets.**

| Category | Examples |
|---|---|
| Database | PostgreSQL URL, user, password |
| Redis / MinIO | Hosts, credentials |
| Auth | JWT signing secret, token lifetimes |
| OTP | Provider settings (sandbox adapter by default) |
| Payment | Provider keys, webhook secret, payment mode, currency (test and live kept separate) |
| Session | Automatic-logout trigger (`TRANSACTION_COMPLETE` or `ORDER_PLACED`) |
| AI | Provider credentials (if any), feature flags (`ai.recommendations.enabled`, `ai.chat.enabled`, `ai.routing.enabled`), kill switch |

## Security principles

- Backend-enforced RBAC and tenant isolation; cross-tenant access returns not-found.
- QR identifiers are not credentials. A guest must verify by OTP; only hashed tokens are stored.
- Payment card data never reaches VENUE NEXUS (hosted gateway page). Webhooks are signature-verified, replay-protected and checked against the stored amount.
- Amounts always come from the persisted order, never from the client.
- AI never decides access, price, status or payment, and never receives OTPs, contacts, QR tokens, device IDs, JWTs or payment data.
- Hash-chained, append-only audit log. Secrets and personal data are kept out of logs.
- Secret scanning runs in CI.

## Sprint roadmap

| Sprint | Focus | Outcome |
|---|---|---|
| 1 | Foundation | Monorepo, auth and RBAC, tenant and venue hierarchy, resources and QR, audit chain, staff dashboard shell, frozen contracts |
| 2 | Customer flow | QR resolve, OTP, sessions, catalogue, cart, idempotent order submission, **End Session**, automatic-close hook, optional AI recommendations and chatbot |
| 3 | Operations and payment | Order lifecycle, routing, assignment, dashboard, customer tracking, **payment, receipts, refunds**, automatic logout on payment, optional AI routing suggestions |
| 4 | Release readiness | Integration, security verification, deployment, runbooks, handover, payment/session/AI verification |

## Documentation

Sprint documents live in `docs/sprints/`. Each sprint has four documents.

| Document | Purpose |
|---|---|
| Phase 1 – PRD | Product requirements, scope, acceptance criteria |
| Phase 2 – TRD | Technical design, APIs, data, security, tests |
| Phase 3 – UI/UX | Screens, flows, states, accessibility |
| Phase 4 – Implementation | Work packages, team allocation, risks, handoff |

Open decisions (payment gateway, AI scope, refund policy, automatic-logout trigger, Sprint 3 capacity) are tracked in the *Open decisions* tables of the sprint documents.

## Branching and contributing

- `main`: stable line.
- `develop`: integrated development.
- `feature/<name>`: individual work, merged through pull requests with review.

Before opening a pull request:

1. Keep OpenAPI, event contracts and schema docs in sync with your change.
2. Add or update tests, including negative and cross-tenant cases.
3. Make sure CI (build, tests, architecture checks, secret scan) passes.
4. Never commit secrets, card data or real personal data.

## License

To be decided. Add a `LICENSE` file before making the repository public.

---

**Team Lead:** Rabeesh R

# VENUE NEXUS

## Sprint 1 -- Technical Requirements Document (TRD)

  Document detail   Value
  ----------------- ---------------------------------------------
  Document type     Technical Requirements Document -- Sprint 1
  Version           1.0
  Date              3 November 2026
  Product           VENUE NEXUS
  Status            Draft for team and mentor review
  Sprint            Sprint 1 -- Foundation

> **Revision note (v1.1):** Change requests CR-01 to CR-05 have been applied. Original content is preserved. Changes are limited to the inline edits listed in Section 15 and the appended Section 15. Items marked *Pending Team Lead confirmation* are proposals, not final decisions.

------------------------------------------------------------------------

## 1. Purpose

This document defines the technical architecture, technology
requirements, backend components, database changes, API conventions,
frontend integration and technical validation required for Sprint 1.

The document is limited to the platform foundation. It describes the
technical work needed to establish the project, freeze the core
contracts, create the initial database schema, implement staff identity
and access control, manage venue hierarchy and physical resources, and
integrate the first React dashboard.

## 2. Sprint 1 Technical Objective

Build a working technical foundation that allows authenticated staff to
manage venue structure and QR-identified resources without crossing
tenant or location boundaries.

The sprint must produce: - A buildable Maven/pnpm monorepo. - Local
development services through Docker Compose. - Approved architecture,
API, event, database, security and deployment documents. - PostgreSQL
migrations V1--V4. - JPA entities and tenant-scoped repositories. -
Staff login, JWT access, refresh rotation, logout and role/scope
checks. - Tenant, organisation, location, space, department and user
APIs. - Resource and QR APIs. - Standard API error handling, trace IDs
and initial audit chain. - React login, role-aware routing and
resource/QR management. - Automated build and test checks.

## 3. Technical Architecture

### 3.1 Architecture Style

VENUE NEXUS uses a domain-oriented Spring Boot modular monolith for
Release 1. The team should not split the initial release into multiple
microservices.

The backend owns: - Business rules. - Input validation. - Data
persistence. - Authentication and authorisation. - Tenant and location
scope enforcement. - QR token issuance and revocation. - Audit
recording.

The React frontend renders screens and collects user input. It must not
become an authority for access decisions.

### 3.2 Sprint 1 Logical Components

``` text
React Venue Dashboard
        |
        | HTTPS / REST
        v
Spring Boot Modular Monolith
        |
        +-- common       API errors, trace ID, pagination
        +-- config      application and security configuration
        +-- identity    staff login and refresh tokens
        +-- security    JWT, roles, tenant and scope context
        +-- venue       tenant and venue hierarchy
        +-- resource    resource registry and QR operations
        +-- audit       append-only audit foundation
        +-- outbox      event persistence foundation
        |
        v
PostgreSQL
        |
        +-- Flyway V1–V4
        +-- tenant and hierarchy data
        +-- staff identity
        +-- resource and QR data
        +-- audit, idempotency and outbox records

Local supporting services:
Docker Compose -> PostgreSQL, Redis and MinIO
```

### 3.3 Backend Layer Rule

The required application flow is:

``` text
Controller -> Service -> Repository
```

-   Controllers receive requests, validate transport-level input and
    return responses.
-   Services implement business rules and authorisation checks.
-   Repositories perform persistence operations.
-   Controllers must not access repositories directly.
-   A module should expose a public service interface rather than
    allowing other modules to access its internal repository or package.

ArchUnit is used to help enforce module boundaries.

## 4. Technology Stack

  -----------------------------------------------------------------------
  Layer                   Technology              Sprint 1 responsibility
  ----------------------- ----------------------- -----------------------
  Backend language        Java 21                 Backend implementation

  Backend framework       Spring Boot 3.x         REST API and modular
                                                  monolith

  Build                   Maven                   Backend dependency and
                                                  build management

  Frontend                React 18, TypeScript,   Staff dashboard
                          Vite                    

  Routing                 React Router            Role-aware application
                                                  routes

  Package manager         pnpm workspaces         Frontend/monorepo
                                                  dependencies

  Database                PostgreSQL 16           Persistent platform
                                                  records

  ORM                     Spring Data JPA,        Entity mapping and
                          Hibernate               repository access

  Migration               Flyway                  Version-controlled
                                                  schema changes

  Security                Spring Security, JWT    Staff authentication
                                                  and authorisation

  Connection pool         HikariCP                Bounded database
                                                  connections

  Cache/infrastructure    Redis                   Local infrastructure;
                                                  Sprint 1 does not
                                                  implement customer
                                                  session state

  Object storage          MinIO in development    Local S3-compatible
                                                  infrastructure

  API documentation       springdoc OpenAPI,      API contract and
                          Swagger UI              frontend integration

  Validation              Jakarta Bean Validation Request validation

  Monitoring              Spring Actuator,        Health and metrics
                          Micrometer              

  Logging                 Logback structured      Traceable application
                          logging and MDC         logs

  Testing                 JUnit 5, Mockito,       Unit and integration
                          Testcontainers          tests

  Architecture checks     ArchUnit                Module boundary checks

  CI                      GitHub Actions          Build, tests and secret
                                                  scan

  QR generation           ZXing or selected       SVG and PNG QR output
                          server-side QR library  

  API verification        Postman                 Endpoint and collection
                                                  tests
  -----------------------------------------------------------------------

## 5. Repository and Project Setup

### 5.1 Monorepo

Create a monorepo containing the backend, frontend and shared
documentation.

The source implementation plan specifies: - `main` branch for the stable
line. - `develop` branch for integrated development. - `feature/<name>`
branches for individual work. - Pull requests for review and
integration.

A suggested high-level structure is:

``` text
venue-nexus/
├── backend/
│   ├── pom.xml
│   └── src/
├── frontend/
│   ├── package.json
│   └── src/
├── docs/
│   ├── architecture.md
│   ├── api-contract.md
│   ├── event-contract.md
│   ├── db-schema.md
│   ├── security.md
│   └── deployment.md
├── docker-compose.yml
└── .github/
    └── workflows/
```

### 5.2 Backend Dependencies

The Spring Boot project must include the dependencies required for the
Sprint 1 foundation: - Spring Web. - Spring Security. - Spring Data
JPA. - Jakarta Validation. - Spring Actuator. - Redis integration. -
Flyway. - PostgreSQL driver. - springdoc OpenAPI. - Micrometer. -
Testcontainers for integration tests.

### 5.3 Local Infrastructure

Docker Compose must provide the development services specified in the
implementation plan: - PostgreSQL. - Redis. - MinIO.

The local configuration must be reproducible for all team members.
Environment-specific secrets must be supplied through environment
configuration and must not be committed to Git.

### 5.4 CI Pipeline

GitHub Actions should run: 1. Backend build. 2. Frontend build. 3. Unit
tests. 4. Architecture tests. 5. Secret scan.

The exact workflow file names and branch protection settings are
implementation choices, but the checks above are part of the Sprint 1
setup requirement.

## 6. Database Technical Requirements

### 6.1 Migration Strategy

All database schema changes must be versioned using Flyway. Do not use
Hibernate `ddl-auto=update` in a shared environment.

Sprint 1 includes four migrations:

  -----------------------------------------------------------------------
  Migration               Tables                  Purpose
  ----------------------- ----------------------- -----------------------
  V1                      `tenant`,               Tenant and venue
                          `organisation`,         hierarchy
                          `location`, `space`,    
                          `department`            

  V2                      `app_user`,             Staff identity and
                          `refresh_token`         refresh-token
                                                  management

  V3                      `resource`,             Physical resource and
                          `resource_qr`           QR identity

  V4                      `audit_log`,            Audit and platform
                          `idempotency_record`,   foundations
                          `outbox_event`          
  -----------------------------------------------------------------------

Migrations must be tested on an empty database. The broader schema plan
also requires forward-only migrations and testing against a previous
version with seed data.

### 6.2 Common Data Rules

-   Tenant-owned tables must contain `tenant_id UUID NOT NULL`, except
    where the schema explicitly defines platform-level exceptions.
-   Every tenant-owned query must be tenant-filtered.
-   Location-scoped access must also apply a location filter.
-   Primary keys use UUIDs; UUIDv7 is recommended by the schema.
-   Timestamps use `TIMESTAMPTZ`, stored in UTC.
-   Passwords and user-presented secrets are stored as hashes, not plain
    text.
-   PostgreSQL is the permanent source of truth.
-   Redis is for temporary/ephemeral state, not the permanent record.

### 6.3 V1 -- Tenant and Venue Hierarchy

The V1 migration creates:

**tenant** - Tenant name. - Tenant status (`ACTIVE` or `SUSPENDED`). -
Optional branding information.

**organisation** - Tenant association. - Organisation name. - Status. -
Unique name within tenant.

**location** - Organisation association. - Location name. - Time zone. -
Currency. - Location configuration columns defined by the source schema.

**space** - Location association. - Optional parent space. - Type
(`BUILDING`, `FLOOR`, `ZONE`). - Name.

**department** - Location association. - Department name. - Department
type. - Unique name within a location and tenant.

### 6.4 V2 -- Staff Identity

The V2 migration creates:

**app_user** - Optional tenant association for the Platform Admin
exception. - Optional organisation, location and department scope. -
Email/login identifier. - Password hash. - Display name. - Role and
status. - Availability. - Failed-login count and lock timestamp. -
Last-login timestamp.

Email uniqueness is case-insensitive. Indexes support tenant/role and
tenant/location/department queries.

**refresh_token** - User association. - SHA-256 token hash. - Token
family identifier. - Expiry. - Revocation timestamp. - Replacement
reference.

Refresh-token records support rotation and reuse detection.

### 6.5 V3 -- Resource and QR Identity

**resource** - Location. - Optional space and department. - Resource
code. - Display name. - Resource type. - Status. - Activation policy. -
JSONB capabilities.

The resource code must be unique within a tenant and location.

**resource_qr** - Resource association. - SHA-256 token hash. -
Version. - Status. - Issuing staff user. - Revocation timestamp.

A partial unique index must enforce one active QR per resource.

### 6.6 V4 -- Platform Foundation

**audit_log** - Tenant and user references where applicable. -
Per-tenant sequence. - Event type and outcome. - Trace ID. - Hashed IP
address where recorded. - Non-sensitive event detail. - Previous row
hash and current row hash.

The log is append-only and hash-chained. The application database user
should have only `INSERT` and `SELECT` privileges for this table.

**idempotency_record** - Request idempotency key. - Scope. - Actor. -
Request hash. - Processing status. - Stored response details. - Expiry.

**outbox_event** - Tenant and location references. - Event type. -
Aggregate type and identifier. - Event payload. - Publication status and
attempts. - Publication timestamp.

Sprint 1 establishes the outbox persistence foundation. Full
workflow/event use is developed across later sprints.

## 7. Backend Implementation Requirements

### 7.1 Common API Layer

Implement: - `ApiError` response model. - `GlobalExceptionHandler`. -
Standard error codes and response format. - `TraceIdFilter`. - Trace ID
in MDC. - `X-Trace-Id` response header. - Shared pagination
validation. - Common validation and time abstractions where specified.

The API error contract must be frozen before the frontend integration
depends on it.

### 7.2 Staff Authentication

The identity implementation must support: - Password verification using
BCrypt or Argon2. - Login lockout behaviour. - `POST /auth/login`. -
Staff access JWT. - Opaque refresh token. - Refresh-token rotation. -
Refresh-family reuse detection. - Logout. - Token denylist. - Security
context population.

The staff access token claims include user ID, token type, role, tenant,
organisation, location, department, issued/expiry times, token ID and
audience as specified by the Security document.

The access token is short-lived; the Security document specifies 15
minutes. The refresh token is opaque, stored by SHA-256 hash and has a
seven-day lifetime with rotation.

### 7.3 Tenant and Scope Context

`StaffJwtFilter` validates the staff token and establishes: -
`TenantContext`. - `ScopeContext`.

These contexts must be cleared after each request.

Tenant identity must not be accepted from a tenant-owned request body,
query parameter or path. The server obtains it from the validated
security context.

All tenant-owned repository access must be tenant-scoped. The
implementation plan specifies a tenant-scoped repository pattern with
methods requiring `tenantId`; a plain unscoped `findById` must not be
exposed for tenant-owned entities.

### 7.4 Role-Based Access Control

Configure: - URL-level security rules in `SecurityConfig`. -
Method-level checks using `@PreAuthorize`. - Deny-by-default
behaviour. - Role and scope validation in service methods.

The React application may hide inaccessible navigation items, but
backend checks remain mandatory.

### 7.5 Hierarchy APIs

Implement endpoints/services for: - Tenants (platform scope). -
Organisations. - Locations. - Spaces. - Departments. - Staff users.

Each operation must validate the caller's role and scope before reading
or changing records.

### 7.6 Resource and QR APIs

Implement resource operations and QR actions: - Create and retrieve
resources. - List resources with shared pagination. - Issue QR
identity. - Render QR as SVG and PNG. - Revoke QR identity. - Reissue QR
identity. - Bulk resource creation with prefix and numeric range, if
retained in the sprint scope.

QR issue must generate 128 random bits, encode the token in base64url
form, store only SHA-256, and return the plain token once. Reissue must
revoke the old identity and create a new one in one transaction.

## 8. API and Contract Requirements

Sprint 1 freezes the shared contracts that later sprints will consume.

Required documents: - `architecture.md` - `api-contract.md` -
`event-contract.md` - `db-schema.md` - `security.md` - `deployment.md`

Contracts to approve: - API naming and versioning conventions. -
Pagination rules. - Standard error format and error codes. - Event
envelope. - Role enum and access matrix. - Staff token claims. - QR
token format and lifecycle. - Database table definitions and
constraints. - Security and logging rules. - Status enums and later
workflow definitions.

The contracts must remain synchronised with code. If an API, event or
schema changes, its corresponding document must be updated in the same
pull request.

## 9. Frontend Technical Requirements

The Sprint 1 React application must include: - Login page. - Dashboard
shell. - Role-aware routing. - Navigation based on authenticated role. -
Permitted location selection. - Hierarchy management pages. - Resource
management pages. - QR generate, preview/print, revoke and reissue
flows. - API client generated from or aligned with OpenAPI.

The UI must not send tenant identifiers for tenant-owned records. Cached
tenant/location data must be cleared on logout and when the active
location changes.

## 10. Security Requirements

The following checks are required: - Valid login succeeds. - Invalid
login returns `401`. - Wrong role returns `403`. - Tenant A cannot read
Tenant B data; the response must not reveal the other tenant's record. -
A location-scoped user cannot read another location's data. - Expired,
tampered, wrong-type and invalid-audience tokens are rejected. - QR
tokens are stored only as hashes. - Revoked QR identities stop
resolving. - Secrets are not committed to Git or written to logs. -
Audit records are append-only and tamper-evident.

## 11. Testing Requirements

  -----------------------------------------------------------------------
  Test level                          Sprint 1 coverage
  ----------------------------------- -----------------------------------
  Unit                                Authentication rules, scope
                                      filtering, QR issue/revoke logic,
                                      validation and error mapping

  API                                 Login success/failure, role access,
                                      tenant isolation, pagination and
                                      resource endpoints

  Integration                         PostgreSQL migrations,
                                      repositories, refresh-token
                                      persistence and audit chain

  Security                            Cross-tenant, cross-location, role
                                      escalation,
                                      expired/tampered/wrong-type token
                                      tests, QR revoke/replay tests

  Frontend                            Login, role-aware routing, resource
                                      create/list and QR management flows

  Architecture                        Module boundary checks using
                                      ArchUnit

  CI                                  Build, tests and secret scan
  -----------------------------------------------------------------------

Testcontainers is specified for database and integration testing.
Postman is used for API collection verification, and the browser flow
must be tested against the integrated backend.

## 12. Sprint 1 Technical Deliverables

1.  Monorepo and branch structure.
2.  Docker Compose development setup.
3.  GitHub Actions build/test/architecture/secret-scan workflow.
4.  Approved architecture, API, event, schema, security and deployment
    contracts.
5.  Flyway V1--V4.
6.  JPA entities and tenant-scoped repositories.
7.  Common error handling and trace ID.
8.  Initial hash-chained audit service.
9.  Staff login, JWT and refresh-token rotation.
10. RBAC and tenant/location scope enforcement.
11. Hierarchy and resource APIs.
12. QR issue, render, revoke and reissue.
13. React login and role-aware dashboard.
14. Resource and QR management pages.
15. Postman collection and test evidence.

## 13. Technical Definition of Done

Sprint 1 technical work is complete when: - The application builds and
starts in the agreed development environment. - Flyway V1--V4 run
successfully on an empty database. - The contracts are reviewed and
approved. - Staff authentication and refresh rotation work. - RBAC and
tenant/location scope tests pass. - Hierarchy and resource endpoints
work. - QR issue/revoke/reissue works and the one-active-QR rule is
enforced. - Error responses include the agreed trace information. -
Audit chain verification succeeds for the initial events. - The React
dashboard works against the live backend. - CI checks pass.

## 14. Source Alignment and Boundaries

This document follows the supplied VENUE NEXUS TRD, Backend Schema,
Backend Implementation Plan and Security and Logging specification. It
does not claim that OTP, customer sessions, ordering, payments, hotel
services, automation or production deployment are Sprint 1 technical
deliverables. Those remain assigned to Sprints 2--4 in the source plan.

## 15. Change Request Technical Additions (CR-01 to CR-05)

### 15.1 Summary

| ID | Change request | Summary |
|---|---|---|
| CR-01 | Remove Lettuce | Lettuce is no longer named as the Redis client/cache. Redis itself is retained for short-lived state (see decision D-1). |
| CR-02 | Payment, transaction and refund | Payment gateway integration through a `PaymentProvider` abstraction, payment lifecycle, receipts and refunds. Contracts in Sprint 1, build in Sprint 3, verification in Sprint 4. |
| CR-03 | AI track (separate plan) | Feature-flagged, advisory AI features planned as a separate track: menu recommendations, customer chatbot, AI-assisted routing suggestions. Never on the critical path or in an exit gate. |
| CR-04 | End Session button | Customer can end their own session at any time while it is PENDING or ACTIVE, including before placing an order. |
| CR-05 | Automatic logout | Customer session is closed automatically when the transaction is complete (configurable trigger). |

### 15.2 Redis client wording (CR-01)

This document no longer names a specific Redis client library. Redis remains local infrastructure for short-lived state. The client choice is an implementation detail recorded in `architecture.md`. See decision D-1 if Redis itself is to be removed.

### 15.3 Contracts to approve (additions to Section 8)

- Payment status enum, refund status enum and the rule that payment status is independent of order status.
- Payment, refund and webhook event envelopes added to `event-contract.md`.
- Customer session closure contract: closure reasons `CLOSED_BY_CUSTOMER`, `CLOSED_AUTO_TRANSACTION_COMPLETE`, `CLOSED_AUTO_ORDER_PLACED`, using the existing `CLOSED` state (no new session state).
- Automatic-logout trigger setting and payment mode setting (names only; storage is added by a later migration).
- AI feature-flag names, AI provider interface and AI data-handling rules.
- New error codes: payment, refund, webhook, session-end and AI errors.

### 15.4 Reserved module boundaries (design only)

| Module | Planned sprint | Boundary rule |
|---|---|---|
| `payment` | Sprint 3 | Owns payment, refund, receipt and webhook records. Other modules use its public service interface only. |
| `ai` | Sprint 2 and 3 | Advisory only. Must not access another module's repository. Receives minimised, tenant-scoped inputs through public service interfaces. |

ArchUnit rules for these boundaries are added when the modules are created. No Sprint 1 migration (V1 to V4) is changed.

### 15.5 Role matrix additions (proposed)

Proposed permission names: `PAYMENT_VIEW`, `REFUND_REQUEST`, `REFUND_APPROVE`, `AI_ADMIN`. Role assignment is *Pending Team Lead confirmation* and mentor approval (D-5, D-6).

### 15.6 Reserved configuration categories

Payment provider credentials and webhook secret, AI provider credentials, AI feature flags, payment mode and automatic-logout trigger. Values come from environment configuration and are never committed to Git.

### 15.7 Testing and definition of done additions

- Contract review checklist covers every item in 15.3.
- Secret scan rules include payment and AI credential patterns.
- Sprint 1 technical work is not complete until the added contracts are approved.

### 15.8 Inline edits made to original text

- Section 4 Technology Stack: the Cache/infrastructure row now reads `Redis` (the word *Lettuce* was removed, CR-01).

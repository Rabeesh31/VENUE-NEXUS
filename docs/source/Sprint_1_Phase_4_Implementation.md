# VENUE NEXUS

## Sprint 1 -- Implementation Plan

  Document detail   Value
  ----------------- ----------------------------------------
  Document type     Sprint Implementation Plan
  Version           1.0
  Date              3 November 2026
  Product           VENUE NEXUS
  Sprint            Sprint 1 -- Foundation
  Team              7 members, including Team Lead Rabeesh

> **Revision note (v1.1):** Change requests CR-01 to CR-05 have been applied. Original content is preserved. Changes are limited to the inline edits listed in Section 16 and the appended Section 16. Items marked *Pending Team Lead confirmation* are proposals, not final decisions.

------------------------------------------------------------------------

## 1. Purpose

This document translates the Sprint 1 PRD, TRD and UI/UX requirements
into a practical development sequence. It identifies the work packages,
technical dependencies, implementation order, integration activities,
tests and Sprint 1 completion criteria.

It is intended to help the team coordinate work, avoid late integration,
and deliver a working foundation rather than disconnected modules.

## 2. Sprint Objective

Deliver the first integrated VENUE NEXUS increment with: - A configured
monorepo and development environment. - Approved architecture, API,
event, role, token, security and database contracts. - PostgreSQL
migrations V1--V4. - Staff authentication, RBAC and tenant/location
scope. - Tenant and venue hierarchy management. - Resource
registration. - QR issue, print, revoke and reissue. - Initial audit
logging and trace IDs. - React login, dashboard shell,
hierarchy/resource screens and QR management. - Passing build, unit,
integration, API and security checks for the Sprint 1 scope.

## 3. Sprint 1 Delivery Flow

The implementation plan requires continuous integration rather than
waiting until the end.

``` text
PLAN
  |
  v
BUILD
  |
  v
TEST
  |
  v
INTEGRATE
  |
  v
TEST AGAIN
  |
  v
REVIEW / MERGE
  |
  v
SPRINT DEMONSTRATION
```

Each completed feature should be tested before integration. After a
merge into `develop`, the integrated application must be tested again.

## 4. Development Rules

1.  Use `main` and `develop` as the shared branches.
2.  Create feature branches using `feature/<name>`.
3.  Use pull requests for code review and integration.
4.  Keep the backend layer rule: Controller → Service → Repository.
5.  Do not allow controllers to access repositories directly.
6.  Every tenant-owned repository operation must be tenant-scoped.
7.  Do not accept tenant identity from client-supplied fields for
    tenant-owned data.
8.  Keep OpenAPI, event contracts and schema documentation synchronised
    with implementation.
9.  Do not commit secrets, passwords, tokens or raw QR values.
10. Repeat integration tests after every merge into `develop`.

## 5. Work Packages

### WP1 -- Project and Repository Setup

**Objective:** Establish a consistent development environment for all
team members.

**Tasks:** - Create the monorepo. - Configure Maven backend project. -
Configure pnpm workspace and React frontend. - Create `main` and
`develop` branches. - Define feature branch naming. - Configure Docker
Compose for PostgreSQL, Redis and MinIO. - Add environment variable
templates without real secrets. - Add GitHub Actions for build and
test. - Add architecture checks and secret scanning.

**Output:** - Repository structure. - Buildable backend and frontend
skeletons. - Local infrastructure setup. - Initial CI pipeline.

**Verification:** - A team member can clone the repository and start the
development environment using the documented steps. - Backend and
frontend build successfully. - CI executes on the configured
branches/pull requests.

### WP2 -- Architecture and Contract Freeze

**Objective:** Agree on the interfaces and rules that all Sprint 1 and
later modules will follow.

**Tasks:** - Prepare `architecture.md`. - Prepare `api-contract.md`. -
Prepare `event-contract.md`. - Prepare `db-schema.md`. - Prepare
`security.md`. - Prepare `deployment.md`. - Approve API naming and
versioning. - Approve pagination conventions. - Approve error response
format and error codes. - Approve event envelope. - Approve role enum
and role matrix. - Approve staff token claims. - Approve QR token format
and lifecycle. - Review database entities, relationships and
constraints.

**Output:** - Reviewed and approved baseline contracts.

**Verification:** - Development team uses the same approved role names,
API conventions, error structure and entity definitions. - Contract
changes are recorded and reviewed before implementation changes.

### WP3 -- Database Foundation

**Objective:** Create the Sprint 1 database schema and persistence
layer.

**Tasks:** 1. Implement Flyway V1 for tenant and venue hierarchy. 2.
Implement Flyway V2 for staff identity and refresh tokens. 3. Implement
Flyway V3 for resources and QR identities. 4. Implement Flyway V4 for
audit, idempotency and outbox records. 5. Create the corresponding JPA
entities. 6. Create tenant-scoped repositories. 7. Add required foreign
keys, unique constraints and indexes. 8. Configure HikariCP with bounded
connection settings. 9. Add Micrometer metrics for connection-pool
observation.

**Expected migration scope:**

  Migration   Tables
  ----------- ---------------------------------------------------
  V1          tenant, organisation, location, space, department
  V2          app_user, refresh_token
  V3          resource, resource_qr
  V4          audit_log, idempotency_record, outbox_event

**Verification:** - Migrations run successfully against an empty
PostgreSQL database. - Foreign keys and unique constraints behave as
expected. - Tenant-owned tables have the required tenant identifiers and
indexes. - Repository methods require tenant scope for tenant-owned
data. - The application starts with the migrated schema.

### WP4 -- Common Backend Foundation

**Objective:** Standardise request handling, error reporting and
traceability.

**Tasks:** - Implement common API error model. - Implement
`GlobalExceptionHandler`. - Map validation, unauthorised, forbidden,
not-found and conflict errors to the agreed response contract. -
Implement `TraceIdFilter`. - Put trace ID in MDC. - Return `X-Trace-Id`
in the response. - Implement shared pagination validation. - Implement
initial `AuditLogService`. - Implement hash-chain calculation and
verification. - Record the first authentication event, `AUTH_LOGIN`.

**Verification:** - API errors match the approved contract. - A request
trace ID can be followed through logs. - The response includes the trace
header. - Audit-chain verification succeeds. - Logs do not contain
secrets.

### WP5 -- Staff Identity and Security

**Objective:** Enable secure staff authentication and enforce
server-side authorisation.

**Tasks:** 1. Implement `AppUser` lookup and account status validation.
2. Implement BCrypt password verification (or the approved
password-hashing choice). 3. Implement failed-login counting and account
lockout. 4. Implement `POST /auth/login`. 5. Issue staff access JWT and
opaque refresh token. 6. Store only the refresh-token SHA-256 hash. 7.
Implement refresh-token rotation. 8. Detect refresh-token family reuse.
9. Implement logout and token denylist. 10. Implement `StaffJwtFilter`.
11. Populate `TenantContext` and `ScopeContext`. 12. Clear security
context after each request. 13. Configure `SecurityConfig`. 14. Add
method-level `@PreAuthorize` checks. 15. Apply organisation/location
scope in service operations. 16. Enforce deny-by-default behaviour.

**Verification:** - Valid login succeeds. - Invalid credentials return
`401`. - Wrong role returns `403`. - Tenant A cannot access Tenant B
records. - Location-scoped users cannot access another location. -
Expired, tampered and wrong-type tokens are rejected. - Refresh rotation
works and reuse detection is tested. - Logout invalidates the relevant
refresh/session path as defined by the security contract.

### WP6 -- Tenant and Venue Hierarchy

**Objective:** Expose the platform and venue structure through backend
services and APIs.

**Tasks:** - Tenant creation and lifecycle endpoints for platform
scope. - Organisation management endpoints. - Location management
endpoints. - Space management endpoints. - Department management
endpoints. - Staff user management endpoints within approved scope. -
Validate parent-child relationships. - Validate tenant ownership and
location scope. - Apply shared pagination to collection endpoints.

**Verification:** - An authorised user can create and retrieve permitted
hierarchy records. - Invalid parent/child associations are rejected. -
Cross-tenant and cross-location access is blocked. - Pagination does not
leak records across tenants.

### WP7 -- Resource Registry and QR

**Objective:** Give physical resources a digital identity.

**Tasks:** - Implement resource entity and repository. - Implement
resource creation and retrieval. - Validate resource type, location,
space and department. - Enforce unique resource code within
tenant/location. - Implement `QrService.issue(resourceId)`. - Generate
128-bit random QR token. - Encode token using base64url. - Store SHA-256
hash only. - Render SVG and PNG output. - Implement QR revocation. -
Record `QR_REVOKED`. - Implement QR reissue in one transaction. -
Enforce one active QR per resource. - Implement bulk creation with
prefix and numeric range if approved.

**Verification:** - QR issuance returns printable output. - The plain
token is returned only once and is not stored. - One active QR exists
per resource. - Revoked QR no longer resolves. - Reissue revokes the old
QR and creates a new active one. - Bulk creation, if delivered, rejects
duplicate or invalid ranges.

### WP8 -- Frontend and API Integration

**Objective:** Deliver the Sprint 1 staff-facing interface connected to
the backend.

**Tasks:** - Create design tokens and shared UI package. - Implement
login screen. - Implement authenticated dashboard shell. - Implement
role-aware routes and navigation. - Implement permitted location
switcher. - Implement tenant/organisation/location screens as
approved. - Implement building/floor/zone hierarchy management. -
Implement department management. - Implement resource list and creation
screens. - Implement QR preview, print, revoke and reissue flows. -
Generate or align API client with OpenAPI. - Handle loading, empty,
success, validation, permission and API error states. - Clear cached
data on logout and active-location change.

**Verification:** - Login works against the live backend. - The
dashboard displays only permitted navigation. - Location switcher
returns only permitted locations. - Resource create/list works. - QR
issue and revoke work from the UI. - Direct API requests remain
protected by backend authorisation.

### WP9 -- Testing, Integration and Sprint Demonstration

**Objective:** Verify that all Sprint 1 modules work together as one
increment.

**Tasks:** - Run backend unit tests. - Run database migration tests. -
Run API tests using Postman. - Run security tests. - Run frontend
tests. - Run integrated browser flow. - Review logs for accidental
secret exposure. - Fix defects. - Review and merge approved pull
requests. - Prepare the Sprint 1 demonstration.

**Demonstration journey:** 1. Sign in as an authorised staff user. 2.
Show role-aware dashboard navigation. 3. Create or select an
organisation/location. 4. Create a building, floor and zone. 5. Create a
department. 6. Register a resource such as Table 12. 7. Generate and
print the resource QR. 8. Revoke the QR and demonstrate that it no
longer resolves. 9. Show that a user from a different tenant/location
cannot access the protected record. 10. Show the corresponding audit
evidence.

## 6. Suggested Team Work Allocation

The project team has seven members, including Team Lead Rabeesh. The
following is a suggested workstream allocation; it is not a claim about
the names or skills of the other six members.

  -----------------------------------------------------------------------
  Workstream                          Suggested responsibility
  ----------------------------------- -----------------------------------
  Team Lead -- Rabeesh                Sprint coordination, requirement
                                      clarification, contract approval
                                      follow-up, integration tracking,
                                      review and demonstration readiness

  Member 2                            Backend project setup, common API
                                      layer and error handling

  Member 3                            Database migrations, JPA entities
                                      and tenant-scoped repositories

  Member 4                            Staff authentication, JWT, RBAC and
                                      scope enforcement

  Member 5                            Tenant hierarchy, department and
                                      resource APIs

  Member 6                            QR issue/revoke/reissue service and
                                      backend tests

  Member 7                            React login, dashboard shell,
                                      hierarchy/resource/QR screens and
                                      frontend integration
  -----------------------------------------------------------------------

The team may adjust this split according to individual skills. Backend
and frontend work must integrate continuously; the allocation must not
create isolated modules that are combined only at the end.

## 7. Suggested Development Sequence

  -----------------------------------------------------------------------
  Sequence                Activity                Dependency
  ----------------------- ----------------------- -----------------------
  1                       Repository, monorepo    None
                          and local               
                          infrastructure          

  2                       Architecture and        Initial repository
                          contract review         

  3                       Flyway V1--V4 and       Schema contract
                          persistence model       

  4                       Common API errors,      Backend skeleton
                          trace IDs and audit     
                          foundation              

  5                       Staff authentication    V2 identity schema
                          and security context    

  6                       RBAC and                Staff authentication
                          tenant/location scope   

  7                       Tenant and hierarchy    V1 schema
                          APIs                    

  8                       Resource and QR APIs    V3 schema and security

  9                       React login and         OpenAPI and auth API
                          dashboard integration   

  10                      Hierarchy/resource/QR   Corresponding APIs
                          UI integration          

  11                      Integrated tests and    All Sprint 1 work
                          demonstration           packages
  -----------------------------------------------------------------------

Some work can run in parallel after the relevant contracts are approved.
Database and API contracts should be agreed before dependent
frontend/backend tasks are built.

## 8. Test Plan

  -----------------------------------------------------------------------
  Category                            Test cases
  ----------------------------------- -----------------------------------
  Build and setup                     Backend/frontend build; Docker
                                      Compose services start; CI workflow
                                      runs

  Database                            V1--V4 apply cleanly; constraints
                                      and indexes are present

  Authentication                      Valid login; invalid login;
                                      lockout; refresh rotation; logout

  Authorisation                       Wrong role; expired token; tampered
                                      token; wrong token type

  Tenant isolation                    Tenant A cannot retrieve Tenant B
                                      data; pagination remains
                                      tenant-scoped

  Location scope                      Location-scoped user cannot access
                                      another location

  Hierarchy                           Create/list tenant, organisation,
                                      location, space and department
                                      within scope

  Resource                            Create/list resource; reject
                                      invalid associations and duplicate
                                      code

  QR                                  Issue, render, revoke, reissue; one
                                      active QR per resource

  Audit                               Login and QR revocation events
                                      recorded; hash chain verifies

  Frontend                            Login, role routing, location
                                      switcher, resource and QR flows

  Logging                             No password, access token, refresh
                                      token or raw QR token in logs
  -----------------------------------------------------------------------

## 9. Sprint 1 Deliverables

1.  Monorepo and branch structure.
2.  Local Docker Compose setup.
3.  CI pipeline with build, tests, architecture checks and secret scan.
4.  Approved architecture, API, event, database, security and deployment
    contracts.
5.  Flyway migrations V1--V4.
6.  JPA entities and tenant-scoped repositories.
7.  Common error handler and trace ID filter.
8.  Initial audit log service with hash chain.
9.  Staff authentication and refresh-token rotation.
10. RBAC and tenant/location scope enforcement.
11. Tenant, organisation, location, space and department APIs.
12. Staff user management endpoints within the approved role model.
13. Resource registry APIs.
14. QR issue, SVG/PNG rendering, revoke and reissue.
15. React login and role-aware dashboard shell.
16. Hierarchy, resource and QR management screens.
17. Postman collection and test evidence.
18. Sprint demonstration and acceptance sign-off.

## 10. Definition of Done

A work item is done only when: - The implementation matches the approved
requirement and contract. - Code is committed to its feature branch. -
Relevant unit/API/integration tests pass. - Tenant and location scope
have been checked where applicable. - No secrets are committed or
logged. - Documentation is updated if the contract or behaviour
changed. - A pull request is reviewed and integrated. - Integrated tests
pass after merge.

## 11. Sprint 1 Exit Criteria

Sprint 1 can be closed when all of the following are demonstrated: -
Staff authentication works. - Role and scope restrictions are enforced
by the backend. - Tenant A cannot access Tenant B data. -
Location-scoped users cannot access another location. - Venue hierarchy
and departments can be managed. - Physical resources can be
registered. - QR identities can be issued and printed. - QR identities
can be revoked and reissued. - The React dashboard is integrated with
the backend. - The initial audit chain verifies. - The application and
database migrations run successfully. - The agreed contracts are
approved. - The required tests pass.

## 12. Risks and Mitigation

  -----------------------------------------------------------------------
  Risk                    Impact                  Mitigation
  ----------------------- ----------------------- -----------------------
  Role matrix not         Rework in backend and   Resolve role enum and
  approved early          UI                      permissions before the
                                                  Sprint 1 gate.

  Database/API contracts  Frontend/backend        Freeze contracts and
  change late             integration delay       update them through
                                                  reviewed pull requests.

  Tenant filtering is     Cross-tenant data       Use tenant-scoped
  implemented             exposure                repository patterns and
  inconsistently                                  explicit security
                                                  tests.

  QR token is stored or   QR misuse and security  Store only SHA-256
  logged in plain form    exposure                hash; return plain
                                                  token once; scan logs
                                                  for secrets.

  Modules integrate only  Integration failures    Use continuous BUILD →
  at sprint end           and delay               TEST → INTEGRATE → TEST
                                                  AGAIN → MERGE.

  Scope expands into      Sprint 1 foundation     Keep OTP, sessions,
  later-sprint work       remains incomplete      ordering, payments,
                                                  hotel services and
                                                  automation out of
                                                  Sprint 1.
  -----------------------------------------------------------------------

## 13. Dependencies and Open Decisions

-   Mentor approval of role enum and authorisation matrix.
-   Confirmation of pilot tenant and location seed data.
-   Approval of API/error/event contracts.
-   Approval of QR output and print expectations.
-   Confirmation whether bulk resource creation is included in Sprint 1.
-   Agreement on team member workstream assignments.

## 14. Explicitly Deferred to Later Sprints

  -----------------------------------------------------------------------
  Later sprint                        Deferred feature groups
  ----------------------------------- -----------------------------------
  Sprint 2                            OTP, customer sessions, session
                                      expiry/rejection, invitations,
                                      catalogue, inventory, cart,
                                      customer PWA, pending-session
                                      queue, location settings and rate
                                      limiting

  Sprint 3                            Workflow engine, orders, kitchen
                                      tickets, department queues,
                                      payment, receipts, notifications,
                                      supervisor recovery and live
                                      operations dashboard

  Sprint 4                            Hotel stays and service requests,
                                      assignment rules, SLA, automation,
                                      reports, row-level security, full
                                      audit verification, final
                                      performance tests and production
                                      deployment
  -----------------------------------------------------------------------

## 15. Source Alignment

This implementation plan follows the VENUE NEXUS Backend Implementation
Plan v1.1, Backend Schema v1.1, PRD v1.0, TRD v1.1, UI/UX Design
Document v1.1 and Security and Logging v1.1. The seven-person work
allocation is a suggested team arrangement and can be changed by the
Team Lead.

## 16. Change Request Additions (CR-01 to CR-05)

### 16.1 Summary

| ID | Change request | Summary |
|---|---|---|
| CR-01 | Remove Lettuce | Lettuce is no longer named as the Redis client/cache. Redis itself is retained for short-lived state (see decision D-1). |
| CR-02 | Payment, transaction and refund | Payment gateway integration through a `PaymentProvider` abstraction, payment lifecycle, receipts and refunds. Contracts in Sprint 1, build in Sprint 3, verification in Sprint 4. |
| CR-03 | AI track (separate plan) | Feature-flagged, advisory AI features planned as a separate track: menu recommendations, customer chatbot, AI-assisted routing suggestions. Never on the critical path or in an exit gate. |
| CR-04 | End Session button | Customer can end their own session at any time while it is PENDING or ACTIVE, including before placing an order. |
| CR-05 | Automatic logout | Customer session is closed automatically when the transaction is complete (configurable trigger). |

### 16.2 Additional tasks for WP2 (Architecture and Contract Freeze)

- Draft payment, refund, webhook and receipt contracts in `api-contract.md` and `event-contract.md`.
- Draft the customer session closure contract and automatic-logout trigger setting.
- Draft AI feature-flag, provider-interface and data-handling rules in `security.md`.
- Add proposed payment, refund and AI rows to the role matrix for mentor approval.
- Reserve audit event types and error codes.
- Remove the Redis client name (*Lettuce*) from `architecture.md` and `deployment.md` (CR-01).

**Owner:** Team Lead (approval follow-up) with Member 2 and Member 3 (contract drafting). **Verification:** contracts reviewed and approved before Sprint 2 starts.

### 16.3 Placement across sprints

| CR | Sprint 1 | Sprint 2 | Sprint 3 | Sprint 4 |
|---|---|---|---|---|
| CR-01 | Wording removed | Wording removed | n/a | Config verified |
| CR-02 | Contracts, enums, roles, events | Order total persisted; no payment | Build: payment, receipt, refund | Verify and release-gate |
| CR-03 | Governance and contracts | Recommendations and chatbot (flagged, optional) | AI routing suggestions (flagged, optional) | Safety/evaluation tests and release decision |
| CR-04 | Contract | Build | Payment-in-flight rule | Verify |
| CR-05 | Contract | Mechanism plus ORDER_PLACED trigger | TRANSACTION_COMPLETE trigger wired to payment | Verify |

### 16.4 Risks added

| Risk | Impact | Mitigation |
|---|---|---|
| Payment, refund and AI decisions stay open | Rework in Sprints 2 to 4 | Resolve D-2 to D-6 before the Sprint 1 gate or record them as tracked open items |
| Sprint 3 overloaded by payment and AI work | Incomplete Sprint 3 | Confirm D-7; keep AI optional and flagged |
| Sensitive data sent to AI or payment logs | Privacy and security exposure | Data-handling rules frozen in `security.md`; log scanning in CI |

### 16.5 Open decisions added

| ID | Decision *(Pending Team Lead confirmation)* |
|---|---|
| D-1 | Redis scope: this change removes the name *Lettuce* only. Redis stays for OTP counters, locks, session status and cart state. Confirm whether Redis itself should also be removed (that would require redesign of the Sprint 2 controls). |
| D-2 | Payment gateway provider, supported payment methods and currencies, and ownership of test/live accounts. |
| D-3 | Payment mode and timing per location: online payment required before staff acceptance or payable any time after submission; offline option (settled outside the platform). |
| D-4 | Automatic-logout trigger: `TRANSACTION_COMPLETE` (proposed default) or `ORDER_PLACED`. |
| D-5 | Refund policy: approving roles, partial refunds, refund window, and whether customers may request refunds. |
| D-6 | AI scope: which AI features are enabled at release, the AI provider (hosted model or rule-based only), and privacy/data-processing approval. |
| D-7 | Sprint 3 capacity: payment, refunds and the AI track add substantial work to a four-day sprint. Confirm whether to extend the sprint or defer refunds/AI. |
| D-8 | Tracking after automatic logout: read-only order-view credential (proposed) or re-verification. |

### 16.6 Definition of done addition

A Sprint 1 work item is also done when any contract it touches includes the CR-02 to CR-05 additions where relevant.

### 16.7 Inline edits made to original text

- None. The existing *Explicitly Deferred to Later Sprints* table is unchanged; placement of the change requests is shown in 16.3.

# VENUE NEXUS

## Sprint 1 -- Product Requirements Document (PRD)

  -----------------------------------------------------------------------
  Document detail                     Value
  ----------------------------------- -----------------------------------
  Document type                       Product Requirements Document --
                                      Sprint 1

  Version                             1.0

  Date                                3 November 2026

  Prepared by                         Rabeesh R, Team Lead

  Product                             VENUE NEXUS -- Multi-Tenant
                                      Physical-to-Digital Operations
                                      Platform

  Status                              Draft for team and mentor review

  Delivery model                      Release 1 -- four sprints
  -----------------------------------------------------------------------

> **Revision note (v1.1):** Change requests CR-01 to CR-05 have been applied. Original content is preserved. Changes are limited to the inline edits listed in Section 14 and the appended Section 14. Items marked *Pending Team Lead confirmation* are proposals, not final decisions.

------------------------------------------------------------------------

## 1. Document Purpose

This document defines the product requirements that must be delivered
during Sprint 1 of VENUE NEXUS. It converts the wider product vision
into a clear, limited set of requirements for the first development
increment.

Sprint 1 establishes the platform foundation. It focuses on the digital
representation of a physical venue and the staff access needed to manage
it. At the end of the sprint, authorised staff should be able to sign
in, manage the venue hierarchy, register physical resources such as
tables and rooms, and issue or revoke QR identities for those resources.

This document is intended to guide the development team, support task
allocation, provide a basis for testing, and establish the Sprint 1
acceptance gate.

## 2. Product Background

VENUE NEXUS is designed as a multi-tenant platform for businesses that
operate physical spaces. A physical resource---such as a restaurant
table, hotel room, counter, seat or desk---is registered in the platform
and given a digital identity through a QR code.

The wider product journey is:

1.  A business configures its tenant, organisation and venue locations.
2.  Physical spaces and resources are registered.
3.  A QR identity is attached to each resource.
4.  A customer later scans the QR code and completes the required
    verification.
5.  The platform establishes a controlled session.
6.  Orders or service requests are processed through trackable
    workflows.
7.  Actions are authorised, measured and audited.

Only the first two foundational capabilities---venue structure and
resource/QR identity---are delivered in Sprint 1. Customer verification
and operational workflows belong to later sprints.

## 3. Sprint 1 Goal

**Sprint goal:** Establish the first usable foundation of VENUE NEXUS by
delivering staff authentication, tenant-aware access, venue hierarchy
management, resource registration and QR identity management.

At the end of Sprint 1, the team should be able to demonstrate the
following end-to-end flow:

1.  A staff user opens the Venue Dashboard.
2.  The staff user signs in with valid credentials.
3.  The backend identifies the user's role, tenant and permitted scope.
4.  The user accesses only the permitted tenant/location information.
5.  An authorised user creates or manages a venue location and its
    spaces/departments.
6.  The user registers a physical resource, such as Table 12 or Room
    305.
7.  The user generates a QR identity and obtains printable output.
8.  The user can revoke or reissue the QR identity.
9.  The relevant actions are recorded in the audit foundation.

## 4. Sprint 1 Scope

### 4.1 Included

-   Platform-level tenant creation, suspension and reactivation.
-   Tenant-level organisation and location management.
-   Location hierarchy using building, floor and zone.
-   Department definition per location.
-   Staff user creation with role and scope.
-   Staff login and secure token handling.
-   Role-based access control (RBAC).
-   Tenant and location scope enforcement.
-   Resource registry.
-   Resource types required by the product.
-   QR issue, print/render, revoke and reissue.
-   Bulk numbered resource creation as a Should requirement, subject to
    sprint capacity and team agreement.
-   Initial audit logging and request trace identification.
-   React login, dashboard shell, role-aware navigation and resource/QR
    management screens.
-   Initial API, event, role, token and database contracts.

### 4.2 Excluded from Sprint 1

The following items are part of Release 1 but are not Sprint 1
deliverables:

-   Customer QR scan landing and customer portal.
-   OTP request, delivery and verification.
-   Customer session creation, confirmation, rejection and expiry.
-   Session invitations and group participation.
-   Menu, categories, items, modifiers and stock.
-   Shared cart and order submission.
-   Kitchen tickets, department queues and task assignment.
-   Payment gateway, payment confirmation and receipts.
-   Hotel stay verification and service requests.
-   Workflow engine and SLA escalation.
-   Automation rules and execution.
-   Advanced reports, enterprise overview and full platform analytics.
-   Full production deployment and final performance benchmarking.

These items must remain assigned to their planned later sprints rather
than being pulled into Sprint 1 without an approved scope change.

## 5. Stakeholders and User Roles

The platform uses role-based access. The backend is the authority for
access decisions; the frontend only displays the navigation and actions
appropriate to the authenticated user.

  -----------------------------------------------------------------------
  Role                    Purpose                 Sprint 1 relevance
  ----------------------- ----------------------- -----------------------
  `PLATFORM_ADMIN`        Operates the platform   Create, suspend and
                          and manages tenant      reactivate tenants
                          lifecycle               

  `TENANT_ADMIN`          Administers a tenant or Manage organisations,
                          organisation            locations and staff
                                                  within scope

  `VENUE_MANAGER`         Runs a venue location   Manage hierarchy,
                                                  resources and QR
                                                  identities

  `SUPERVISOR`            Oversees a department   Receives only permitted
                          or shift                location-level access

  `STAFF`                 Performs daily venue    Receives only permitted
                          operations              location/department
                                                  access

  Customer/Guest          Uses the QR-based       No customer-facing
                          customer experience     session functionality
                                                  is delivered in Sprint
                                                  1
  -----------------------------------------------------------------------

The role list and authorisation matrix are subject to mentor approval
before the Sprint 1 gate. If the mentor changes the role model, the role
enum and permission matrix must be updated before implementation is
treated as final.

## 6. Functional Requirements

Priority definitions: - **Must:** Required for Sprint 1 acceptance. -
**Should:** Expected where capacity allows, but not a blocker unless the
team/mentor promotes it to Must.

  -----------------------------------------------------------------------
  ID                      Requirement             Priority
  ----------------------- ----------------------- -----------------------
  S1-FR-01                Platform Admin can      Must
                          create, suspend and     
                          reactivate tenants.     

  S1-FR-02                Tenant Admin can manage Must
                          organisations and       
                          locations inside the    
                          tenant's scope.         

  S1-FR-03                A location can contain  Must
                          a building, floor and   
                          zone hierarchy.         

  S1-FR-04                Departments can be      Must
                          defined per location,   
                          including examples such 
                          as kitchen,             
                          housekeeping and front  
                          desk.                   

  S1-FR-05                Staff users can be      Must
                          created with a role and 
                          scope at tenant,        
                          organisation or         
                          location level, with an 
                          optional department.    

  S1-FR-06                A resource can be       Must
                          registered with an      
                          identifier, type,       
                          location, parent space, 
                          status, capabilities    
                          and activation policy.  

  S1-FR-07                The resource registry   Must
                          supports table, room,   
                          counter, seat, desk,    
                          bed and equipment       
                          types.                  

  S1-FR-08                A resource can receive  Must
                          an unguessable QR       
                          identity with printable 
                          output.                 

  S1-FR-09                QR identities can be    Must
                          revoked or reissued,    
                          and a revoked token     
                          must stop resolving     
                          immediately.            

  S1-FR-10                Numbered resources can  Should
                          be created in bulk, for 
                          example Tables 1--20.   

  S1-FR-11                Staff authentication,   Must
                          role checks and         
                          tenant/location scope   
                          restrictions are        
                          enforced by the         
                          backend.                

  S1-FR-12                Security events are     Must
                          recorded in the initial 
                          append-only audit log.  
  -----------------------------------------------------------------------

## 7. Detailed Functional Description

### 7.1 Tenant Management

A tenant represents a business/customer account using VENUE NEXUS.
Platform-level administration must support creating a tenant and
changing its lifecycle status.

A tenant has a name, status and optional branding information. The
status values defined in the schema are `ACTIVE` and `SUSPENDED`.

Sprint 1 requirements: - A Platform Admin can create a tenant. - A
Platform Admin can suspend or reactivate a tenant. - Tenant-owned
records must remain associated with the correct tenant. - Tenant
identity for normal tenant operations must be derived from the validated
staff token/security context, not supplied by the browser.

### 7.2 Organisation and Location Management

An organisation represents a business grouping within a tenant. A
location represents an operating site or branch.

A tenant may contain one or more organisations, and an organisation may
contain multiple locations. A location stores the basic information
needed to identify and configure the site, including its name, time zone
and currency.

Sprint 1 requirements: - Tenant Admin can create and manage
organisations within scope. - Tenant Admin can create and manage
locations within scope. - A location must belong to an organisation. -
Location data must be tenant-scoped. - A user who is restricted to one
location must not be able to access another location's data.

### 7.3 Space Hierarchy

A location contains a physical hierarchy represented by `space` records.

The intended hierarchy is:

``` text
Tenant
└── Organisation
    └── Location
        └── Building
            └── Floor
                └── Zone
```

A space has a type and name. A parent space allows nested structures. A
building may be a root space, a floor may be its child, and a zone may
be nested under a floor.

The interface should make this hierarchy understandable and allow
authorised users to create and view the permitted levels.

### 7.4 Department Management

Departments are defined within a location. Examples include: - Floor
service - Kitchen - Bar - Housekeeping - Maintenance - Front desk -
Other

Each department has a name and type and belongs to a location.
Department assignment later supports staff scope and operational
routing, but Sprint 1 only establishes the department records and
associations.

### 7.5 Staff Identity and Access

Staff users are represented by the `app_user` entity. A staff account
includes a login email, password hash, display name, role, status and
optional organisation, location and department scope.

The account must not store a reversible password. BCrypt or Argon2
hashing is specified by the source design.

The user scope is determined by the assigned identifiers: - A location
scope limits access to that location. - An organisation scope limits
access to that organisation. - Otherwise, a tenant-level user may access
the permitted tenant scope. - A Platform Admin is platform-scoped and is
the exception to the tenant foreign-key requirement.

The final role-to-permission mapping must be approved before the sprint
gate.

### 7.6 Resource Registry

A resource represents a physical object or area that needs a digital
identity.

The registry must support: - Resource code, such as `T12` or `R305`. -
Display name. - Resource type. - Location. - Optional space. - Optional
department. - Status. - Activation policy. - Capabilities, represented
as structured data.

The source schema defines resource types such as `TABLE`, `ROOM`,
`COUNTER`, `SEAT`, `DESK`, `BED`, `EQUIPMENT` and `OTHER`.

The resource status values include `AVAILABLE`, `OCCUPIED` and
`OUT_OF_SERVICE`. The wider lifecycle behaviour that changes these
statuses is delivered with customer sessions in Sprint 2; Sprint 1
establishes the resource data and initial management.

### 7.7 QR Identity Management

Each resource may be assigned a QR identity. A QR code is an identifier,
not a customer credential and not an authorisation mechanism.

Sprint 1 QR capabilities: 1. Generate a cryptographically random token
with 128 bits of randomness. 2. Encode the token in a URL/QR
representation. 3. Store only the SHA-256 hash of the token. 4. Return
the plain token only once during issuance so the QR can be rendered. 5.
Generate printable SVG and PNG output. 6. Revoke an existing QR
identity. 7. Reissue a QR identity by revoking the previous active
identity and issuing a new one in a transaction. 8. Enforce one active
QR identity per resource.

The system must not treat possession of a QR code as proof of customer
identity or permission to order. Customer verification and session
authority are later-sprint features.

### 7.8 Audit Foundation

The audit log is append-only and hash-chained. Sprint 1 establishes the
service and records the first security event, including `AUTH_LOGIN`,
and the QR revocation event `QR_REVOKED`.

Audit details must not include passwords, raw access/refresh tokens, QR
token values or unnecessary personal data. The audit record includes
trace identification and a hash-chain structure to make tampering
detectable.

## 8. User Stories

  -----------------------------------------------------------------------
  ID                                  User story
  ----------------------------------- -----------------------------------
  US-01                               As a Platform Admin, I want to
                                      create and manage tenants so that
                                      businesses can be onboarded to the
                                      platform.

  US-02                               As a Tenant Admin, I want to create
                                      organisations and locations so that
                                      branches can be represented
                                      correctly.

  US-03                               As a Venue Manager, I want to
                                      define buildings, floors and zones
                                      so that the physical structure of a
                                      location is organised.

  US-04                               As a Venue Manager, I want to
                                      create departments so that venue
                                      teams can be represented.

  US-05                               As an authorised administrator, I
                                      want to create staff accounts with
                                      roles and scopes so that each
                                      person receives the correct access.

  US-06                               As a staff member, I want to sign
                                      in securely so that I can use the
                                      dashboard.

  US-07                               As a Venue Manager, I want to
                                      register tables or rooms so that
                                      every physical resource has a
                                      record in the platform.

  US-08                               As a Venue Manager, I want to
                                      generate and print QR identities so
                                      that physical resources can be
                                      connected to the digital platform.

  US-09                               As a Venue Manager, I want to
                                      revoke or reissue a QR identity so
                                      that an old QR code can no longer
                                      be used.
  -----------------------------------------------------------------------

## 9. Business Rules and Constraints

1.  Backend authorisation is mandatory; hiding a frontend button is not
    sufficient.
2.  Tenant identity comes from the validated staff token/security
    context.
3.  Every tenant-owned query must filter by tenant.
4.  Location-scoped users must additionally be restricted by location.
5.  A tenant-owned record must never be returned to a different tenant.
6.  A resource code must be unique within its location and tenant.
7.  A resource may have only one active QR identity at a time.
8.  A revoked QR token must no longer resolve.
9.  Plain QR tokens must not be stored in the database.
10. Staff passwords must be hashed, not reversibly encrypted.
11. All schema changes must be managed through Flyway.
12. Customer OTP/session authority is not part of Sprint 1.

## 10. Acceptance Criteria

Sprint 1 is accepted only when the following agreed checks pass:

### Authentication and authorisation

-   Valid staff credentials allow login.
-   Invalid credentials return an unauthorised response (`401`).
-   A user attempting an action outside their role receives a forbidden
    response (`403`).
-   An attempt to access another tenant's record returns no data and
    follows the not-found behaviour (`404`) specified by the project.
-   A location-scoped user cannot access a different location's records.
-   Expired, tampered or wrong-type tokens are rejected.

### Venue hierarchy

-   An authorised user can create and retrieve an organisation and
    location.
-   An authorised user can create and retrieve building, floor and zone
    records.
-   An authorised user can create and retrieve departments within a
    location.
-   Records are visible only within the user's tenant and scope.

### Resources and QR

-   An authorised user can create and retrieve resources.
-   Resource type and location associations are validated.
-   QR issue returns printable QR output.
-   Only one active QR exists per resource.
-   Revoking a QR prevents it from resolving.
-   Reissuing a QR revokes the previous one and creates a new active
    identity.

### Audit and integration

-   Login and QR revocation audit events are recorded.
-   Audit data does not contain secrets.
-   The React login and resource/QR management flows work against the
    integrated backend.
-   The approved contracts are available to the team.

## 11. Dependencies and Open Decisions

The following decisions must be resolved or explicitly tracked: - Mentor
approval of the final role enum and role-to-permission matrix. -
Confirmation of the pilot tenant, organisation and location seed data. -
Confirmation of the required location fields. - Confirmation of the QR
print layout and branding requirements. - Confirmation whether bulk
resource creation remains a Should item or is promoted to Must. -
Confirmation of API error codes and the final error response contract.

Do not invent a final decision for an item marked open in the source
specifications.

## 12. Sprint 1 Exit Gate

Sprint 1 is complete when:

-   Staff can authenticate.
-   The backend enforces role, tenant and location scope.
-   An authorised user can create a venue location and its hierarchy.
-   Resources can be registered against the correct location and space.
-   QR identities can be generated, printed, revoked and reissued.
-   Tenant-isolation and location-scope tests pass.
-   Audit events are recorded.
-   The frontend is integrated with the backend.
-   API, event, role, token and database contracts are approved.

## 13. Source Alignment

This Sprint 1 PRD is derived from the VENUE NEXUS Product Requirements
Document v1.0, Technical Requirements Document v1.1, Backend Schema
v1.1, Backend Implementation Plan v1.1, UI/UX Design Document v1.1 and
Security and Logging v1.1. It preserves the original Sprint 1 allocation
and does not move later-sprint features into this sprint.

## 14. Change Request Additions (CR-01 to CR-05)

### 14.1 Purpose

This section records change requests applied after the original Sprint 1 PRD (v1.0). Sprint 1 delivers **no** payment, AI or session-end functionality. Its responsibility is to freeze the contracts, roles, events and governance rules so that Sprints 2 to 4 can add these capabilities without reworking the foundation.

### 14.2 Change request summary

| ID | Change request | Summary |
|---|---|---|
| CR-01 | Remove Lettuce | Lettuce is no longer named as the Redis client/cache. Redis itself is retained for short-lived state (see decision D-1). |
| CR-02 | Payment, transaction and refund | Payment gateway integration through a `PaymentProvider` abstraction, payment lifecycle, receipts and refunds. Contracts in Sprint 1, build in Sprint 3, verification in Sprint 4. |
| CR-03 | AI track (separate plan) | Feature-flagged, advisory AI features planned as a separate track: menu recommendations, customer chatbot, AI-assisted routing suggestions. Never on the critical path or in an exit gate. |
| CR-04 | End Session button | Customer can end their own session at any time while it is PENDING or ACTIVE, including before placing an order. |
| CR-05 | Automatic logout | Customer session is closed automatically when the transaction is complete (configurable trigger). |

### 14.3 Placement across sprints

| CR | Sprint 1 | Sprint 2 | Sprint 3 | Sprint 4 |
|---|---|---|---|---|
| CR-01 | Wording removed | Wording removed | n/a | Config verified |
| CR-02 | Contracts, enums, roles, events | Order total persisted; no payment | Build: payment, receipt, refund | Verify and release-gate |
| CR-03 | Governance and contracts | Recommendations and chatbot (flagged, optional) | AI routing suggestions (flagged, optional) | Safety/evaluation tests and release decision |
| CR-04 | Contract | Build | Payment-in-flight rule | Verify |
| CR-05 | Contract | Mechanism plus ORDER_PLACED trigger | TRANSACTION_COMPLETE trigger wired to payment | Verify |

### 14.4 Additional Sprint 1 requirements

| ID | Requirement | Priority |
|---|---|---|
| S1-FR-13 | Contracts for payment, refund, customer session end, automatic logout and AI features are drafted and approved in the Sprint 1 contract freeze (enums, events, error codes, configuration names). No implementation in Sprint 1. | Must |
| S1-FR-14 | The role and permission matrix includes proposed rows for viewing payments, requesting refunds, approving refunds and administering AI features. | Must |
| S1-FR-15 | Audit event types are reserved for payment, refund, session end and AI use (for example `PAYMENT_SUCCEEDED`, `REFUND_REQUESTED`, `SESSION_ENDED`, `AI_FEATURE_USED`) without secrets or personal data in event detail. | Must |
| S1-FR-16 | A feature-flag convention (per tenant/location, default OFF) is defined for AI features. | Should |

### 14.5 Scope clarification

- Payment gateway, payment confirmation and receipts remain excluded from Sprint 1 (Section 4.2). They are planned for Sprint 3.
- AI feature implementation is not part of Sprint 1. Only governance and contracts are in scope.
- The End Session button and automatic logout are customer-portal features delivered in Sprint 2. Sprint 1 only freezes the session-closure contract.

### 14.6 Additional business rules

1. Payment status is a separate concept from the operational order/request status. No competing order status values are introduced.
2. AI output is advisory. It never decides access, price, payment, order status or tenant scope.
3. QR tokens, OTP values, contact details, device identifiers, JWTs and payment secrets must never be sent to an AI provider or written to payment/AI logs.
4. Ending a customer session is authorised and enforced by the backend, not the browser.

### 14.7 Additional acceptance criteria

- [ ] Payment, refund, session-end and AI contracts are reviewed and approved with the existing Sprint 1 contracts.
- [ ] The role matrix includes the proposed payment, refund and AI rows and is approved by the mentor.
- [ ] Reserved audit event types are documented in `security.md` and `event-contract.md`.

### 14.8 Open decisions added

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

### 14.9 Inline edits made to original text

- None. Sprint 1 exclusions (including payment and receipts) remain accurate; they are clarified in 14.5.

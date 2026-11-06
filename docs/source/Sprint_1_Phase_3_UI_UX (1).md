# VENUE NEXUS

## Sprint 1 -- UI/UX Design Document

  Document detail   Value
  ----------------- --------------------------------------------------
  Document type     UI/UX Design Specification -- Sprint 1
  Version           1.0
  Date              3 November 2026
  Product           VENUE NEXUS
  Frontend          React, TypeScript, React Router, pnpm workspaces
  Status            Draft for implementation

> **Revision note (v1.1):** Change requests CR-01 to CR-05 have been applied. Original content is preserved. Changes are limited to the inline edits listed in Section 15 and the appended Section 15. Items marked *Pending Team Lead confirmation* are proposals, not final decisions.

------------------------------------------------------------------------

## 1. Purpose

This document describes the user interface and user experience to be
delivered in Sprint 1. It focuses on the staff-facing Venue Dashboard
foundation: login, role-aware navigation, venue hierarchy management,
resource management and QR identity management.

The purpose is to ensure that the team builds a consistent, usable and
secure management interface that is connected to the Sprint 1 backend
APIs.

The wider UI/UX design includes a customer portal, staff operations
queues, ordering, payments, hotel services and reports. Those screens
belong to later sprints and are deliberately excluded from the Sprint 1
implementation scope.

## 2. Sprint 1 UX Goal

Deliver a staff-facing dashboard that allows an authorised user to: 1.
Sign in. 2. See navigation appropriate to their role. 3. Select only a
location that is within their permitted scope. 4. View and manage the
venue hierarchy. 5. Create and manage departments. 6. Register and
manage physical resources. 7. Generate, preview, print, revoke and
reissue QR identities.

The interface should communicate system status clearly and provide
feedback for every action.

## 3. UX Principles

  -----------------------------------------------------------------------
  Principle                           Sprint 1 application
  ----------------------------------- -----------------------------------
  Role-based                          Show only navigation and actions
                                      relevant to the authenticated role.

  Secure by design                    Never rely on hidden buttons or
                                      routes as the security control; the
                                      backend authorises every operation.

  Clear feedback                      Every save, login, QR action and
                                      error has visible feedback.

  Consistent                          Reuse the same design tokens, form
                                      patterns, buttons, tables, dialogs
                                      and status styles.

  Accessible                          Use visible labels, keyboard focus,
                                      readable contrast and status
                                      text/icons.

  Responsive                          Ensure management screens remain
                                      usable on desktop, tablet and
                                      phone.

  Forgiving but safe                  Confirm actions such as QR
                                      revocation and provide
                                      understandable validation errors.

  Honest status                       Display the actual resource/QR
                                      state returned by the backend.
  -----------------------------------------------------------------------

The source UI/UX specification sets WCAG 2.1 AA as the accessibility
target. Sprint 1 should establish the patterns needed to meet that
target.

## 4. Users and Role-Based Interface

  -----------------------------------------------------------------------
  Role                                Sprint 1 interface responsibility
  ----------------------------------- -----------------------------------
  `PLATFORM_ADMIN`                    Platform-level tenant management
                                      entry points.

  `TENANT_ADMIN`                      Tenant/organisation-level hierarchy
                                      and staff administration entry
                                      points.

  `VENUE_MANAGER`                     Venue management: locations,
                                      spaces, departments, resources and
                                      QR identities.

  `SUPERVISOR`                        Only the permitted location-level
                                      screens/actions available to this
                                      role.

  `STAFF`                             Only the permitted screens/actions
                                      available to the staff member's
                                      scope.
  -----------------------------------------------------------------------

The final role matrix must come from the backend/security specification
and be approved by the mentor. The frontend must not invent permissions.

## 5. Sprint 1 Application Structure

### 5.1 Dashboard Shell

The dashboard provides the shared layout around the Sprint 1 management
pages.

Suggested layout:

``` text
+------------------------------------------------------------------+
| VENUE NEXUS     Location switcher              Profile / Logout  |
+----------------------+-------------------------------------------+
| Sidebar              | Page title                 Primary action |
|                      |-------------------------------------------|
| Dashboard            |                                           |
| Locations            | Main content                              |
| Hierarchy            | Tables / forms / hierarchy views          |
| Departments          |                                           |
| Resources            |                                           |
| QR Management        |                                           |
|                      |                                           |
+----------------------+-------------------------------------------+
```

The precise navigation labels can be refined during implementation, but
the areas required for Sprint 1 must be represented.

### 5.2 Responsive Behaviour

-   On desktop and tablet, use a persistent sidebar and a top bar.
-   On phone, the sidebar may collapse to a compact navigation pattern.
-   Keep the primary action visible and reachable.
-   Tables must remain usable on narrow screens through responsive
    columns, horizontal scrolling or a compact list representation.
-   Forms must not require horizontal scrolling.

### 5.3 Shared Interface Components

Create reusable UI components/design tokens for: - Colour and
typography. - Spacing and layout. - Buttons and links. - Text fields and
selects. - Form validation. - Tables and list rows. - Status labels. -
Confirmation dialogs. - Toast/success/error feedback. - Loading
indicators and empty states.

## 6. Navigation and Information Architecture

A suggested Sprint 1 navigation structure is:

``` text
Venue Dashboard
├── Dashboard
├── Tenant / Organisation (role-dependent)
├── Locations
├── Space Hierarchy
├── Departments
├── Staff Users (if included in the approved Sprint 1 role scope)
├── Resources
└── QR Management
```

Navigation must be role-aware. The backend remains the authority and
must reject direct requests to resources outside the user's role or
scope.

A location switcher must list only locations returned by the backend for
the authenticated user. When the active location changes, cached data
associated with the previous location must be cleared.

## 7. Screen Specifications

### 7.1 Login Screen

**Purpose:** Authenticate staff and establish a secure dashboard
session.

**Required elements:** - VENUE NEXUS application identity. - Email
field. - Password field. - Sign-in button. - Loading indicator while the
request is being processed. - Generic error area for failed login. -
Appropriate handling for locked or expired login state.

**Interaction:** 1. User enters email and password. 2. User selects Sign
in. 3. Frontend submits credentials to the authentication API. 4. While
waiting, the button shows progress and repeated submission is prevented.
5. On success, the application stores/handles credentials according to
the approved security design and routes the user to the permitted
dashboard. 6. On failure, the UI displays a generic message and does not
disclose whether the email address exists.

**States to design:** - Initial. - Submitting. - Successful. - Invalid
credentials. - Account locked. - Network/API error. - Session expired.

### 7.2 Dashboard Home / Shell

**Purpose:** Provide a consistent starting point after login.

**Required elements:** - Application logo/name. - Current user
display/profile. - Location switcher when relevant. - Role-appropriate
navigation. - Main content area. - Logout action.

The Sprint 1 dashboard is a foundation shell. Full live operations,
order queues, analytics and reporting dashboards are later-sprint work.

**Behaviour:** - The application obtains user and scope details from the
authenticated backend context. - The location selector shows only
authorised locations. - Switching location refreshes relevant page data
and clears previous-location cached data. - Logout clears cached
tenant/location data and returns to the login screen.

### 7.3 Tenant / Organisation / Location Screens

**Purpose:** Allow permitted administrators to create and manage the
hierarchy above physical spaces.

**Tenant screen:** - Tenant name. - Status. - Branding information where
supported. - Create, suspend or reactivate actions for Platform Admin.

**Organisation screen:** - Organisation name. - Status. - Parent tenant
context. - Create/list/detail behaviour according to Tenant Admin
permissions.

**Location screen:** - Location name. - Organisation. - Time zone. -
Currency. - Status. - Other location configuration fields only where the
Sprint 1 API exposes them.

Do not implement later-sprint session, tax workflow or OTP configuration
screens merely because related columns appear in the full database
schema. The Sprint 1 screen should expose only the fields approved for
the first release increment.

### 7.4 Space Hierarchy Screen

**Purpose:** Show and manage the physical structure within a location.

The hierarchy is:

``` text
Location
└── Building
    └── Floor
        └── Zone
```

**Required interactions:** - View the hierarchy. - Create a building. -
Create a floor under a building. - Create a zone under a floor. -
Display the parent-child relationship clearly. - Validate required names
and types. - Show a clear error if a parent does not belong to the
active location or scope.

The interface should use a tree/list or nested hierarchy representation.
The final visual pattern can be selected by the design team.

### 7.5 Department Management Screen

**Purpose:** Maintain the departments associated with a location.

**Required fields:** - Department name. - Department type. - Location
association.

The source schema lists department types such as floor service, kitchen,
bar, housekeeping, maintenance, front desk and other.

**Required interactions:** - List departments for the permitted
location. - Create a department. - Edit permitted department details if
the API supports editing. - Display validation and duplicate-name
errors. - Show an empty state when no departments exist.

### 7.6 Resource Management Screen

**Purpose:** Register and maintain the physical objects that will
receive QR identities.

**Resource list should display, where available:** - Resource code. -
Display name. - Resource type. - Location. - Space. - Department. -
Status.

**Resource creation form:** - Resource code. - Display name. - Resource
type. - Location. - Optional space. - Optional department. - Activation
policy. - Capabilities, where supported by the product interface.

**Resource types:** - Table. - Room. - Counter. - Seat. - Desk. - Bed. -
Equipment. - Other.

**Interactions:** 1. User opens Resources. 2. The list displays
resources within the active permitted scope. 3. User selects Create
Resource. 4. User enters the required details. 5. Frontend validates
basic field requirements. 6. Backend validates ownership, associations,
uniqueness and permissions. 7. On success, the resource appears in the
list. 8. On failure, the interface displays a useful message without
exposing internal data.

**Bulk creation:** The PRD classifies numbered resource creation (for
example Tables 1--20) as Should. If the team includes it, the UI may
provide: - Resource type. - Prefix. - Starting number. - Ending
number. - Location/space association.

The backend must validate the range and prevent duplicate resource
codes. This feature should not displace Must requirements if sprint
capacity is limited.

### 7.7 QR Management Screen

**Purpose:** Manage the QR identity associated with a resource.

**Suggested list information:** - Resource code/name. - QR status. - QR
version. - Issued date/issuer where returned by the API. - Available
action.

**QR issue flow:** 1. User selects a resource. 2. User selects Generate
QR. 3. Frontend requests issuance from the backend. 4. Backend returns
the one-time plain token/QR representation for rendering. 5. UI displays
a QR preview and print option. 6. The raw token is not retained or
exposed unnecessarily.

**QR revoke flow:** 1. User selects Revoke. 2. UI shows a confirmation
dialog. 3. User confirms. 4. Backend revokes the QR. 5. UI refreshes
status and displays success feedback. 6. The revoked QR must no longer
resolve.

**QR reissue flow:** 1. User selects Reissue. 2. UI explains that the
existing QR will be invalidated. 3. User confirms. 4. Backend revokes
the previous active QR and creates a new one in one transaction. 5. UI
displays the new QR preview and print action.

**Important security behaviour:** - Do not persist raw QR tokens in
browser storage. - Do not put raw token values into logs. - Do not treat
QR possession as customer authentication or session authority. - Display
active/revoked status with text and/or an icon, not colour alone.

## 8. Form and Interaction Standards

-   Every input must have a visible label.
-   Required fields must be identified.
-   Validation errors should appear near the relevant field.
-   Preserve user-entered non-sensitive values after a recoverable
    validation error.
-   Disable repeated submission while a request is pending.
-   Destructive actions require confirmation.
-   Success and error feedback must be visible and understandable.
-   Avoid displaying stack traces, SQL errors or internal implementation
    details.
-   Permission errors should explain that the user does not have access,
    without revealing protected data.

## 9. UI States Checklist

Every Sprint 1 screen must be designed and checked for:

  -----------------------------------------------------------------------
  State                               Expected behaviour
  ----------------------------------- -----------------------------------
  Loading                             Show progress or skeleton while
                                      data is fetched.

  Empty                               Explain that no records exist and
                                      show an authorised next action.

  Populated                           Display the returned records
                                      clearly.

  Validation error                    Identify the field or rule that
                                      needs correction.

  API error                           Show a safe, useful error message.

  Permission denied                   Do not show protected data; show an
                                      access-denied state.

  Stale conflict                      Explain that data changed and
                                      provide a refresh/retry path where
                                      safe.

  Success                             Confirm the completed action.

  Small screen                        Keep navigation, forms and actions
                                      usable.

  Keyboard                            Support keyboard navigation and
                                      visible focus.

  Screen reader                       Use meaningful labels and status
                                      text.
  -----------------------------------------------------------------------

## 10. Security and Tenant Isolation in the UI

-   The frontend must not send `tenantId` for tenant-owned operations.
-   The location switcher must use locations returned by the backend for
    the current user.
-   Cached data must be cleared on logout.
-   Cached data must be cleared when the active location changes.
-   A resource identifier from another tenant must not reveal that
    tenant's data.
-   Raw QR tokens must not be kept in persistent browser storage.
-   Sensitive values must not appear in URLs after the relevant flow.
-   The backend must authorise every request even when the UI hides a
    route or button.

## 11. Accessibility and Responsive Requirements

-   Target WCAG 2.1 AA as stated in the source UI/UX specification.
-   Maintain readable text contrast.
-   Do not communicate status by colour alone.
-   Provide visible focus indicators.
-   Ensure keyboard access to forms, tables, menus and dialogs.
-   Use accessible labels for icon-only controls.
-   Make buttons and touch targets usable on phone and tablet.
-   Keep layouts responsive across desktop, tablet and phone.

## 12. Sprint 1 UI Acceptance Checklist

-   [ ] Login supports initial, submitting, success, invalid, locked and
    error states.
-   [ ] Successful login routes the user to an authorised dashboard.
-   [ ] Role-aware navigation is displayed.
-   [ ] Backend authorisation is enforced for direct API calls and
    routes.
-   [ ] Location switcher lists only permitted locations.
-   [ ] Tenant/organisation/location screens expose only approved Sprint
    1 operations.
-   [ ] Building/floor/zone hierarchy can be viewed and managed.
-   [ ] Department records can be listed and created.
-   [ ] Resources can be created and listed.
-   [ ] QR identities can be generated and previewed.
-   [ ] QR output can be printed.
-   [ ] QR revocation and reissue include confirmation.
-   [ ] Logout clears cached tenant/location data.
-   [ ] Loading, empty, error, success and permission-denied states are
    handled.
-   [ ] Responsive and keyboard checks are completed.

## 13. Explicitly Out of Scope for Sprint 1

Do not implement the following screens in Sprint 1: - Customer scan
landing. - Customer OTP screens. - Pending, rejected, expired or active
customer session screens. - Session timer and invitations. - Menu, item
details and shared cart. - Order tracking, payment and receipt. - Staff
pending-session queue. - Catalogue and stock management. - Location
session settings. - Hotel verification and service request queues. -
Automation builder. - Reports and audit viewer. - Full enterprise and
platform overview dashboards.

These are allocated to Sprints 2--4 in the source UI/UX plan.

## 14. Source Alignment

This document is based on the VENUE NEXUS UI/UX Design Document v1.1,
PRD v1.0, TRD v1.1 and the Sprint 1 backend implementation plan. It
retains the source document's Sprint 1 mapping: design tokens, shared UI
package, login, dashboard shell, role routing, hierarchy/resource
management, and QR generation/printing.

## 15. Change Request UI Additions (CR-01 to CR-05)

### 15.1 Summary

| ID | Change request | Summary |
|---|---|---|
| CR-01 | Remove Lettuce | Lettuce is no longer named as the Redis client/cache. Redis itself is retained for short-lived state (see decision D-1). |
| CR-02 | Payment, transaction and refund | Payment gateway integration through a `PaymentProvider` abstraction, payment lifecycle, receipts and refunds. Contracts in Sprint 1, build in Sprint 3, verification in Sprint 4. |
| CR-03 | AI track (separate plan) | Feature-flagged, advisory AI features planned as a separate track: menu recommendations, customer chatbot, AI-assisted routing suggestions. Never on the critical path or in an exit gate. |
| CR-04 | End Session button | Customer can end their own session at any time while it is PENDING or ACTIVE, including before placing an order. |
| CR-05 | Automatic logout | Customer session is closed automatically when the transaction is complete (configurable trigger). |

### 15.2 Design-only additions for Sprint 1

No new screens are implemented in Sprint 1. The shared design system adds the following patterns so later sprints stay consistent:

- **Payment status badges** (pending, processing, paid, failed, cancelled) and **refund status badges**, each with text and an icon, never colour alone.
- **Receipt component** layout (reference, items, totals, date).
- **Session-ended notice** pattern (neutral, non-error tone, clear next step).
- **AI-generated content label** and an AI assistant panel pattern, clearly distinguished from venue-authored content.
- **Persistent session control** slot in the customer portal header for the End Session button.
- Role-aware navigation placeholders for Payments and Refunds in the staff dashboard (hidden until built).

### 15.3 Accessibility

All added patterns follow the existing WCAG 2.1 AA target. Status changes and the session-ended notice must be announced to assistive technology.

### 15.4 Sprint 1 UI acceptance additions

- [ ] Design tokens and component patterns in 15.2 are documented in the shared UI package.
- [ ] No payment, AI or end-session screen is built or exposed in Sprint 1.

### 15.5 Inline edits made to original text

- None.

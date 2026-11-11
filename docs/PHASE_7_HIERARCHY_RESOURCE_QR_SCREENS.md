# VENUE NEXUS: Sprint 1, Phase 7
## Hierarchy, Department, Resource and QR Screens (Frontend)

> **How to use this file (instructions to the coding agent / Antigravity):**
> Read this whole file before writing code. Follow it as the specification for Phase 7.
> Where this file says **"CONFIRM"**, the source documents do not define the answer. Do **not** invent it.
> Inspect the repository and the approved `docs/api-contract.md` / OpenAPI spec first, and if the answer is still missing, make the smallest safe assumption, mark it with a `// TODO(confirm): ...` comment, and list it in the PR description.

---

## 1. Mission

Build the staff-facing **management screens** of the VENUE NEXUS Venue Dashboard that let an authorised user:

1. Manage the **tenant / organisation / location** records they are permitted to see (only the operations the Sprint 1 API exposes).
2. View and manage the **space hierarchy**: Location > Building > Floor > Zone.
3. Manage **departments** of a location.
4. **Create and list resources** (Table 12, Room 305, ...).
5. **Issue, preview, print, revoke and reissue QR identities** for resources.

Every screen must handle **loading, empty, success, validation, permission-denied and API-error** states.

**Phase 7 deliverable (from the Sprint 1 Phase Allocation):**
> Resource create and list work, QR issue and revoke work from the UI, and direct API requests remain protected by backend authorisation.

**Owner:** Yuvan Nandan (Frontend team). **Frontend Lead:** Lackshana (Phase 6). **Team Lead / integrator:** Rabeesh R (Phase 8).

---

## 2. Project context (short)

VENUE NEXUS is a multi-tenant platform where every physical resource (table, room, counter, seat, desk) gets a digital identity (a QR code). Sprint 1 builds only the **foundation**: staff login, venue hierarchy, resources, QR identity, audit and trace IDs.

- Architecture: Spring Boot modular monolith (backend) + React/TypeScript dashboard (frontend). The backend is the **only** authority for access decisions. The frontend renders and collects input.
- Hierarchy: `Tenant > Organisation > Location > Building > Floor > Zone`.
- A QR code is an **identifier, not a credential**. It grants no customer authority.

### Frontend stack (fixed)
React 18, TypeScript, Vite, React Router, pnpm workspaces. API client generated from / aligned with the OpenAPI spec (Phase 6 sets this up).

### Where Phase 7 sits relative to other phases

| Phase | Owner | Relationship to you |
|---|---|---|
| 6: Frontend foundation, login, dashboard shell | Lackshana | **You consume it**: design tokens, shared UI package, auth/session handling, role-aware routes, nav, location switcher, API client. **Do not rebuild these.** |
| 4: Tenant, hierarchy, department APIs | Ashwin | Backend endpoints your hierarchy/department/location screens call |
| 5: Resource registry + QR service | Monisha | Backend endpoints your resource/QR screens call |
| 3: Staff identity & security | Steffina | Auth, 401/403 behaviour, token handling |
| 8: Integration & demo | Rabeesh | Merges your PR, runs the integrated flow and demo |

If a Phase 6 building block you need does not exist yet (button, table, dialog, toast, API client, route guard), **build the smallest local version inside your feature folder** and note it in the PR so it can be moved into the shared package later. Do not edit Phase 6 files except to register your routes/nav entries.

---

## 3. Hard rules (non-negotiable)

### Security and tenant isolation
1. **Never send `tenantId`** in any request body, query or path for tenant-owned data. Tenant comes from the token on the server. (`locationId` and other parent IDs are sent only where the approved API contract requires them.)
2. **Hiding a button or route is not security.** The backend authorises every call. The UI must still behave correctly when the backend answers `403` or `404`.
3. **Never read or invent permissions on the client.** Show or hide navigation and actions using the role/scope data that Phase 6 exposes from the authenticated context. Do not hard-code a private permission matrix. The final role matrix is pending mentor approval.
4. **Location list comes only from the backend** (via the Phase 6 location switcher). Never hard-code locations.
5. **Clear cached data on logout and on active-location change.** Use the mechanism Phase 6 provides. If you add your own caches (e.g. React Query), key them by active location and clear them on both events.
6. A resource/record ID from another tenant must show a normal "not found" state, never any data.

### QR token handling (critical)
7. The **raw QR token / QR URL is returned only once** at issue/reissue time. Keep it **in component memory only** (React state).
8. **Never** put the raw token in `localStorage`, `sessionStorage`, `IndexedDB`, cookies, URL query/hash, React Query persisted cache, Redux persist, or any other storage that survives a reload or logout.
9. **Never** `console.log` the token, QR data URL, or full API responses of QR issue/reissue. Do not include it in error messages, toasts, analytics or test snapshots.
10. Discard the token from memory when the QR dialog/preview closes, on navigation away, on logout and on location change.
11. Do not treat QR possession as authentication. Do not build any customer scan/landing behaviour.

### Code and process
12. TypeScript strict. No `any` unless unavoidable and commented.
13. No secrets, tokens or real credentials in the repo, tests, fixtures or screenshots.
14. Show **safe** error messages only: no stack traces, SQL or internal details. Showing the `traceId` from the error body for support is allowed.
15. Do not expand scope into later sprints (see section 12).

---

## 4. Discovery steps (do these FIRST, before coding)

The API paths, field names and enums are defined in `docs/api-contract.md` and the backend OpenAPI spec, which were not included in the planning documents. Do not guess them.

1. Inspect the repo layout. The planning docs show both `frontend/` (TRD) and `apps/` + `packages/` (PRD v1.1). Find where the dashboard app and shared UI package actually live and follow the existing structure.
2. Read `docs/api-contract.md`, `docs/security.md`, `docs/db-schema.md`, and the OpenAPI spec (Swagger UI of the running backend or the generated client).
3. Inspect what Phase 6 already provides: API client, auth context (user, role, scope), route guards, navigation config, location switcher, design tokens, shared components, toast/dialog primitives, query/caching approach.
4. Check the generated API client for endpoints covering: organisations, locations, spaces, departments, resources, resource QR (issue / revoke / reissue / render SVG & PNG), and bulk creation (if present).
5. Write a short mapping table in your PR description: *screen > endpoint > request/response type used*. If an endpoint you need does not exist, **stop and flag it** (Backend owners: Ashwin for hierarchy/department, Monisha for resource/QR) instead of faking data. Local mocks are fine for development only and must be removed or clearly isolated before the PR.

---

## 5. Pages and routes

Register these inside the Phase 6 dashboard shell. Final route names/labels may follow the Phase 6 navigation config; the **areas** below must exist.

| Area | Suggested route | Nav label |
|---|---|---|
| Tenant / Organisation / Location | `/organisations`, `/locations` (tenant screen only for Platform Admin) | Organisation, Locations |
| Space hierarchy | `/hierarchy` | Space Hierarchy |
| Departments | `/departments` | Departments |
| Resources | `/resources` (list), `/resources/new` (create) | Resources |
| QR management | `/qr` (and/or QR actions inside resource list/detail) | QR Management |

- Routes are role-aware through the Phase 6 route guard. Unauthorised access shows an **access-denied state** (no protected data).
- Staff Users screen: **not in Phase 7** (not listed in your steps). Do not build it unless Rabeesh assigns it.
- Payments / Refunds nav placeholders belong to the Phase 6 design-only additions, not to you.

---

## 6. Screen specifications

### 6.1 Tenant / Organisation / Location screens (Step 7.1)

Implement **only what the Sprint 1 API exposes and the Team Lead approved**. **CONFIRM** with Rabeesh/Ashwin which of these are in scope per role.

| Screen | Content | Who (per PRD, role-dependent) |
|---|---|---|
| Tenant | Name, status (`ACTIVE` / `SUSPENDED`), branding where supported. Actions: **create, suspend, reactivate** | `PLATFORM_ADMIN` only |
| Organisation | Name, status, parent tenant context. List / create / detail | `TENANT_ADMIN` within scope |
| Location | Name, organisation, time zone, currency, status. List / create / detail | `TENANT_ADMIN` within scope; `VENUE_MANAGER` view/manage own location as the API allows |

Rules:
- A **location must belong to an organisation**. The create form requires choosing one (from the backend list).
- Suspend / reactivate tenant: **confirmation dialog**, success toast, status badge updated from the backend response (text + icon, not colour only).
- **Do NOT add** session settings, OTP settings, tax, pending-wait, idle-timeout, rejection-cooldown or other later-sprint location configuration fields, even if columns exist in the DB schema. Expose only fields approved for Sprint 1.
- Collections use shared pagination (`page`, `size`, `sort`, max size 100). Use approved sort fields only.

### 6.2 Space hierarchy screen (Step 7.1)

Purpose: view and manage the physical structure of the **active location**.

```
Location
└── Building
    └── Floor
        └── Zone
```

Required interactions:
- **View** the hierarchy as a tree or nested list. Show parent-child clearly. Expand/collapse must be keyboard accessible (proper tree/list semantics, labels).
- **Create a building** (root space of the location).
- **Create a floor** under a selected building.
- **Create a zone** under a selected floor.
- Fields: name (required), type (derived from the level: `BUILDING` / `FLOOR` / `ZONE`), parent (selected by the user, not typed).
- Do not allow invalid nesting in the UI (no zone directly under a building, no floor under a zone). The backend still validates; show its error if it rejects.
- Show a clear error if the parent does not belong to the active location/scope (backend `404`/`409`/`400`: map to a friendly message).
- Empty state: "No buildings yet" with an authorised "Add building" action.
- Editing/deleting spaces: **only if the API supports it** (the Sprint 1 docs list create/view). Otherwise do not add it.

### 6.3 Department management screen (Step 7.2)

- Fields: **name** (required), **type** (required), location (the active location, not free-typed).
- Department types from the source schema: **floor service, kitchen, bar, housekeeping, maintenance, front desk, other**. **CONFIRM** exact enum values from the API contract (likely upper snake case such as `FLOOR_SERVICE`, `FRONT_DESK`) and display friendly labels.
- Interactions: list departments of the permitted location; create; **edit only if the API supports it**; show validation and **duplicate-name errors** (name is unique within a location); empty state when none exist.

### 6.4 Resource list and creation screens (Step 7.3)

**List columns (where the API returns them):** resource code, display name, type, location, space, department, status, QR status (if returned or loaded separately).

**Resource types:** `TABLE`, `ROOM`, `COUNTER`, `SEAT`, `DESK`, `BED`, `EQUIPMENT`, `OTHER`.
**Resource statuses (display only in Sprint 1):** `AVAILABLE`, `OCCUPIED`, `OUT_OF_SERVICE`. Sprint 1 does not change status through sessions.
**Activation policies:** `STAFF_CONFIRM`, `STAY_VERIFY`, `OPEN`. Show as a select with plain-language help text. Note from the PRD: `OPEN` is for low-risk resource types only, so show a hint but let the **backend** enforce it. **CONFIRM** the default value.

**Create form fields:**
| Field | Required | Notes |
|---|---|---|
| Resource code | Yes | e.g. `T12`, `R305`. Unique within tenant + location (backend enforces; show duplicate error `409` clearly, keep the user's input) |
| Display name | Yes | e.g. "Table 12" |
| Resource type | Yes | enum above |
| Location | Yes | active location, prefilled and not free-typed |
| Space | Optional | choose from the active location's hierarchy |
| Department | Optional | choose from the active location's departments |
| Activation policy | Yes | enum above |
| Capabilities | Optional | only if the API/contract defines a shape (structured JSON); otherwise omit. **CONFIRM** |

Flow: open Resources > list shows resources in active scope > **Create Resource** > client-side basic validation > submit > backend validates ownership, associations, uniqueness, permissions > on success the resource appears in the list with a success toast > on failure show a useful safe message.

**Bulk creation (Should, not Must):**
- Build it **only after** all Must items are done **and** Rabeesh confirms bulk creation is included in Sprint 1.
- Fields: resource type, prefix, start number, end number, location/space association.
- Client validation: start and end are integers, start ≤ end, sensible max range (the **backend** defines the real limit; **CONFIRM**). Show a preview ("Will create T1 ... T20").
- Backend rejects duplicates and invalid ranges; surface its message.

Pagination: list uses `page`, `size`, `sort`. Handle a loading skeleton, empty state, and error with retry.

### 6.5 QR management: preview, print, revoke, reissue (Step 7.4)

**List information (where returned):** resource code/name, **QR status** (e.g. active / revoked / none), QR version, issued date/issuer, available action. QR status always shown as **text + icon**, never colour alone.

**Issue flow**
1. User selects a resource with no active QR and chooses **Generate QR**.
2. Frontend calls the issue endpoint (no `tenantId`).
3. Backend returns the one-time QR representation (SVG/PNG and/or the token/URL). **CONFIRM** the exact response shape. Prefer rendering the SVG/PNG **returned by the backend**. Only generate a QR client-side from the token if the contract explicitly says so.
4. Show a **QR preview dialog/panel** with the resource code/name, the QR, a **Print** action and (if the API provides them) **Download SVG/PNG**.
5. Token is held **in memory only** and cleared when the preview closes (see rule 7-10).

**Print**
- Provide a clean **print view** (a print stylesheet or a dedicated print route/dialog with `@media print`) that shows only: QR image + resource code/name (+ tenant/location branding **only if the print layout is approved; CONFIRM**). Hide the dashboard chrome in print.
- Printing must not write the token to the URL, storage or logs.
- Make sure the QR is large enough and has sufficient quiet zone to scan.

**Revoke flow**
1. User selects **Revoke** on an active QR.
2. **Confirmation dialog**: explain that this QR will stop working immediately and cannot be un-revoked (a new one can be issued).
3. On confirm: call revoke, disable the button while pending, refresh status from the backend, show success feedback.
4. If the backend answers with an error (e.g. already revoked, not found, forbidden), show a clear message and refresh the row.

**Reissue flow**
1. User selects **Reissue** on a resource with an active QR.
2. Dialog explains: "The existing QR will be invalidated and a new one created."
3. On confirm: backend revokes the old QR and creates a new one **in one transaction**; the UI then shows the **new QR preview and print action** and refreshes status.
4. Make sure the old QR preview is not still visible or retained.

**One active QR per resource:** the UI must not offer "Generate QR" while an active QR exists; offer **Reissue** instead. The backend still enforces this.

**Security display notes:** status text + icon for Active / Revoked; no raw token in the DOM longer than necessary (do not render it as visible text; if the contract returns a URL, do not display it as plain text unless the design requires it).

### 6.6 Cross-cutting behaviour for every screen (Step 7.5)

Implement a consistent state set (see section 9) using shared components, and apply the form standards in section 8.

---

## 7. Data fetching, state and errors

- Use the generated/aligned **API client from Phase 6**. Do not hand-write duplicate fetch wrappers.
- Server state: follow the pattern Phase 6 established (e.g. React Query). If none exists, use a small hook-per-resource pattern with loading/error/data states.
- **Cache key must include the active `locationId`.** On location change and on logout, clear/invalidate all location-scoped data (and QR in-memory state).
- After create/revoke/reissue, **refetch** from the backend; show the backend's actual state ("honest status"), not an optimistic guess.
- Prevent double-submit: disable submit buttons and show progress while a request is pending.
- If an `Idempotency-Key` is required by the contract for create operations, generate a UUID per submit attempt and reuse it on retry of the same attempt. **CONFIRM** whether it applies to Sprint 1 endpoints.
- Error handling (frozen error format):
  ```json
  { "timestamp": "...", "status": 409, "error": "ERROR_CODE", "message": "...", "path": "...", "traceId": "..." }
  ```
  | HTTP | Code (examples) | UI behaviour |
  |---|---|---|
  | 400 | `VALIDATION_ERROR` | Show field-level errors near the fields; keep input |
  | 401 | `UNAUTHENTICATED` | Handled by the Phase 6 client (refresh or redirect to login). Do not duplicate it |
  | 403 | `FORBIDDEN` | Access-denied state/message; no protected data |
  | 404 | `NOT_FOUND` | "Not found or not available to you" (also covers other-tenant records) |
  | 409 | duplicate / conflict codes (**CONFIRM** codes) | Explain the conflict (e.g. resource code already exists) and offer refresh/retry where safe |
  | 5xx | `INTERNAL_ERROR` | Generic safe message with retry; show `traceId` as a reference |
  Map by `error` code first, then by status. Never display raw `message` text if it could contain internal detail; prefer your own friendly mapping with the backend message as a fallback only for validation errors.

---

## 8. Form and interaction standards

- Every input has a **visible label**; required fields are marked.
- Validation errors appear **next to the field** (and are announced to screen readers).
- Preserve non-sensitive input after a recoverable validation error.
- Disable repeated submission while pending.
- Destructive actions (revoke, reissue, suspend tenant) need a **confirmation dialog**.
- Success and error feedback is always visible (toast/inline alert) and announced (`aria-live`).
- No stack traces, SQL or internals in messages.
- Permission errors say the user does not have access, without revealing protected data.

---

## 9. UI states checklist (every screen)

| State | Required behaviour |
|---|---|
| Loading | Skeleton/spinner while fetching |
| Empty | Explain that nothing exists yet + an authorised next action |
| Populated | Clear table/list/tree |
| Validation error | Field-level, near the field |
| API error | Safe message + retry + `traceId` reference |
| Permission denied | Access-denied state, no protected data |
| Stale conflict | Explain data changed; offer refresh/retry |
| Success | Confirmation feedback |
| Small screen | Usable on phone: tables scroll horizontally or collapse to a compact list; forms never need horizontal scroll |
| Keyboard | Full keyboard operation, visible focus |
| Screen reader | Meaningful labels and status text |

---

## 10. Accessibility and responsive requirements (target: WCAG 2.1 AA)

- Text contrast at least 4.5:1; visible focus indicators; touch targets at least 44 px.
- Status never by colour alone: always **text + icon + colour** (QR Active/Revoked, tenant Active/Suspended, resource status).
- Dialogs: focus trap, `Esc` to close, return focus to the trigger, labelled title, `role="dialog"`/`alertdialog` for destructive confirmations.
- Icon-only buttons have accessible names.
- Tables: proper `<th scope>`, caption or `aria-label`; the tree has correct roles and arrow-key behaviour or is a simple nested list with expandable buttons.
- Respect reduced motion.
- Responsive: desktop and tablet use the persistent sidebar from Phase 6; the phone layout keeps primary actions reachable.

---

## 11. Suggested file structure (adapt to the repo's real layout)

```
<dashboard-app>/src/features/
├── hierarchy/
│   ├── pages/        TenantsPage, OrganisationsPage, LocationsPage, SpaceHierarchyPage
│   ├── components/   SpaceTree, SpaceForm, LocationForm, OrganisationForm, TenantStatusBadge
│   └── hooks/        useLocations, useSpaces, useOrganisations ...
├── departments/
│   ├── pages/        DepartmentsPage
│   └── components/   DepartmentForm, DepartmentTypeSelect
├── resources/
│   ├── pages/        ResourcesPage, ResourceCreatePage (or dialog), BulkResourceDialog (optional)
│   ├── components/   ResourceTable, ResourceForm, ResourceTypeSelect, ActivationPolicySelect
│   └── hooks/        useResources, useCreateResource
└── qr/
    ├── pages/        QrManagementPage
    ├── components/   QrPreviewDialog, QrPrintView, QrStatusBadge, RevokeConfirmDialog, ReissueConfirmDialog
    └── hooks/        useIssueQr, useRevokeQr, useReissueQr   (in-memory result only)
```

Keep feature code self-contained; import shared UI/API client from Phase 6; do not import another feature's internals.

---

## 12. Out of scope for Phase 7 / Sprint 1 (do NOT build)

Customer scan landing, customer OTP screens, pending/rejected/expired/active session screens, session timer, invitations, menu/cart/orders/payment/receipts, staff pending-session queue, catalogue and stock, **location session/OTP/tax settings**, hotel verification and service requests, automation builder, reports and audit viewer, full enterprise/platform dashboards, payment/refund/AI screens, End Session / automatic logout UI (Sprint 2), Staff Users screen (unless assigned), login and dashboard shell (Phase 6).

---

## 13. Testing (Step 7.5 support and Phase 8 handoff)

Add tests with the project's existing frontend test setup (likely Vitest + React Testing Library; **CONFIRM** what Phase 6 set up):

- **Hierarchy:** tree renders from API data; create building/floor/zone calls the right endpoint without `tenantId`; invalid nesting is blocked in the UI; backend error shown.
- **Departments:** list, create, duplicate-name error, empty state.
- **Resources:** list renders; create form validation (required fields); duplicate code `409` keeps input and shows the message; `403` shows access-denied; empty/loading/error states.
- **QR:** issue shows preview; token is **not** present in any storage (`localStorage`, `sessionStorage`) after issue and after closing the dialog; revoke requires confirmation; reissue requires confirmation and replaces the preview; "Generate" is not offered when an active QR exists; Active/Revoked shown as text + icon.
- **Isolation:** switching the active location clears resource/department/hierarchy data and QR in-memory state; logout clears them.
- **Accessibility:** run an automated a11y check (e.g. `jest-axe`) on each page and dialog if available; manually check keyboard flow and focus.
- Manual check on desktop, tablet and phone widths.
- Verify with the live backend: create Table 12, issue QR, print, revoke, reissue; confirm a direct API call by a user without permission still returns `403` (UI hiding is not the control).

Run before every push: `pnpm lint`, `pnpm typecheck` (or `tsc --noEmit`), `pnpm test`, `pnpm build`. Fix all failures.

---

## 14. Git workflow and pull request (for integration with Phase 8)

Follow the project workflow: `main` > `develop` > `feature/<name>`.

1. Start from an up-to-date `develop`:
   ```bash
   git checkout develop
   git pull origin develop
   git checkout -b feature/phase-7-hierarchy-resource-qr-screens
   ```
2. Commit in small logical steps (suggested order), each building and passing lint/tests:
   1. `feat(hierarchy): tenant/organisation/location screens`
   2. `feat(hierarchy): space hierarchy tree and create flows`
   3. `feat(departments): department list and create`
   4. `feat(resources): resource list and create`
   5. `feat(qr): issue, preview and print`
   6. `feat(qr): revoke and reissue with confirmation`
   7. `test: phase 7 screens and QR token-handling tests`
   8. *(only if approved)* `feat(resources): bulk create`
3. Push and open a **pull request into `develop`** (not `main`):
   ```bash
   git push -u origin feature/phase-7-hierarchy-resource-qr-screens
   ```
4. Do **not** commit secrets, `.env` files, tokens, raw QR values or real credentials. The CI secret scan must pass.
5. After merge, Rabeesh (Phase 8) re-runs the integrated tests; be available to fix defects.

### PR description template

```markdown
## Phase 7: Hierarchy, Department, Resource and QR screens

### What this PR adds
- [ ] Tenant / organisation / location screens (as approved)
- [ ] Space hierarchy (building / floor / zone)
- [ ] Department management
- [ ] Resource list + create (bulk: yes/no)
- [ ] QR preview, print, revoke, reissue

### Screen > endpoint mapping
| Screen | Endpoint | Notes |
|---|---|---|

### Assumptions / CONFIRM items (need answers before merge)
- ...

### Security checklist
- [ ] No tenantId sent from the client
- [ ] Raw QR token held in memory only; not in any storage, URL or console
- [ ] Cache cleared on logout and on location change
- [ ] Destructive actions confirmed
- [ ] Backend errors handled (400/401/403/404/409/5xx) with safe messages

### UI states checklist
- [ ] Loading  [ ] Empty  [ ] Validation  [ ] API error  [ ] Permission denied  [ ] Success
- [ ] Keyboard + focus  [ ] Phone/tablet widths  [ ] WCAG basics (labels, contrast, no colour-only status)

### Tests
- [ ] lint  [ ] typecheck  [ ] unit/component tests  [ ] build
- Manual run against the live backend: <what was verified>

### Screenshots (no tokens or secrets visible)
```

---

## 15. Acceptance checklist (Phase 7 is done when all are true)

- [ ] Tenant / organisation / location screens expose **only approved Sprint 1 operations**
- [ ] Building / floor / zone hierarchy can be viewed and created with correct parent-child links
- [ ] Departments can be listed and created; duplicate names show a clear error
- [ ] Resources can be created and listed against the correct location, space and department
- [ ] QR can be generated, previewed and **printed**
- [ ] QR **revoke** and **reissue** each require confirmation and refresh the real backend status
- [ ] Raw QR token is never persisted or logged
- [ ] Logout and location change clear cached tenant/location data
- [ ] Loading, empty, validation, API-error, permission-denied and success states exist on every screen
- [ ] Responsive (desktop/tablet/phone) and keyboard checks completed; status never colour-only
- [ ] Direct API calls by unauthorised users are still rejected by the backend (verified, not assumed)
- [ ] No later-sprint features built
- [ ] lint, typecheck, tests and build pass; CI green; PR opened into `develop` with the template filled in

---

## 16. Open items to confirm (ask Rabeesh / backend owners; do not guess)

| # | Question | Who |
|---|---|---|
| 1 | Exact API paths, request/response fields and enums (hierarchy, department, resource, QR) | Rithik (contract), Ashwin, Monisha |
| 2 | Which tenant/organisation/location operations are in Sprint 1 UI scope per role | Rabeesh |
| 3 | QR issue response shape (SVG/PNG from backend vs token for client rendering) and whether download is offered | Monisha |
| 4 | QR print layout and branding requirements (PRD open decision) | Rabeesh / mentor |
| 5 | Is bulk resource creation included in Sprint 1 (Should) and what is the max range | Rabeesh / Monisha |
| 6 | Department type enum values; whether edit/delete exists for departments and spaces | Ashwin |
| 7 | Resource `capabilities` shape and default activation policy | Monisha / Rithik |
| 8 | Conflict/duplicate error codes and whether `Idempotency-Key` applies to create endpoints | Rithik |
| 9 | Final role matrix (what each role may see/do) | Mentor via Rabeesh |
| 10 | Which frontend test tooling Phase 6 set up | Lackshana |

---

*Source documents used: PRD v1.1 (Venue Nexus), Sprint 1 PRD, Sprint 1 TRD, Sprint 1 UI/UX Design Document, Sprint 1 Implementation Plan (WP8, WP9) and the Sprint 1 Phase Allocation (Phase 7 steps 7.1 to 7.5).*

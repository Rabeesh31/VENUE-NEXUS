# VENUE NEXUS - Open Decisions & Proposals Registry

**Document Title:** Open Decisions & Proposals Registry  
**Version:** 0.2-DRAFT  
**Status:** DRAFT - awaiting approval by Rabeesh R  
**Owner:** Rithik Saran (Backend Lead)  
**Date:** 8 November 2026  

## Change Log
| Version | Date | Author | Description |
|---|---|---|---|
| 0.1-DRAFT | 6 November 2026 | Rithik Saran | Initial compilation of change-request decisions D-1..D-8, PRD/TRD open items, and Phase 1 technical proposals. |
| 0.2-DRAFT | 8 November 2026 | Rithik Saran | Updated TP-11 (database-level counter table audit_chain and row locking implemented per Team Lead review note; added to Phase 2 review items) and OI-06 (Contract ready, awaiting frontend lead confirmation). |

---

## 1. Non-Negotiable Governance Rule
As stipulated in the Product Requirements Document (PRD v1.1) and Technical Requirements Document (TRD v1.1):
> *"Do not invent a final decision for an item marked open. Anything not stated in the source docs must be written as 'PROPOSED - pending Team Lead (Rabeesh R) / mentor approval' and listed in docs/open-decisions.md. Never present a guess as a decision."*

All items listed below represent proposals pending formal confirmation.

---

## 2. Change Request Decisions (D-1 to D-8 Verbatim)

| ID | Decision Item *(Verbatim from PRD/TRD Section 14/15/16)* | Status |
|---|---|---|
| **D-1** | **Redis scope:** this change removes the name *Lettuce* only. Redis stays for OTP counters, locks, session status and cart state. Confirm whether Redis itself should also be removed (that would require redesign of the Sprint 2 controls). | *Pending Team Lead confirmation* |
| **D-2** | **Payment gateway provider, supported payment methods and currencies, and ownership of test/live accounts.** | *Pending Team Lead confirmation* |
| **D-3** | **Payment mode and timing per location:** online payment required before staff acceptance or payable any time after submission; offline option (settled outside the platform). | *Pending Team Lead confirmation* |
| **D-4** | **Automatic-logout trigger:** `TRANSACTION_COMPLETE` (proposed default) or `ORDER_PLACED`. | *Pending Team Lead confirmation* |
| **D-5** | **Refund policy:** approving roles, partial refunds, refund window, and whether customers may request refunds. | *Pending Team Lead confirmation* |
| **D-6** | **AI scope:** which AI features are enabled at release, the AI provider (hosted model or rule-based only), and privacy/data-processing approval. | *Pending Team Lead confirmation* |
| **D-7** | **Sprint 3 capacity:** payment, refunds and the AI track add substantial work to a four-day sprint. Confirm whether to extend the sprint or defer refunds/AI. | *Pending Team Lead confirmation* |
| **D-8** | **Tracking after automatic logout:** read-only order-view credential (proposed) or re-verification. | *Pending Team Lead confirmation* |

---

## 3. PRD & Implementation Plan Open Items

| Item # | Open Requirement Description | Impacted Area | Current Status |
|---|---|---|---|
| **OI-01** | **Mentor approval of final role enum and role-to-permission matrix.** Source roles: `PLATFORM_ADMIN`, `TENANT_ADMIN`, `VENUE_MANAGER`, `SUPERVISOR`, `STAFF`. | Security, Identity, UI routing | PROPOSED - pending mentor approval |
| **OI-02** | **Confirmation of pilot tenant, organisation, and location seed data.** Seed values needed for Phase 2/3 test automation and sprint demonstration. | Flyway V1 seed, Demo | PROPOSED - pending Team Lead confirmation |
| **OI-03** | **Confirmation of required location configuration columns.** Base columns: name, time zone, currency. Additional settings (e.g. tax identifier, contact) open. | Location entity / DB Schema | PROPOSED - pending Team Lead confirmation |
| **OI-04** | **Confirmation of QR print layout and branding requirements.** Print dimensions, SVG/PNG styling tokens, and tenant logo embedding. | Resource QR service | PROPOSED - pending UI/UX confirmation |
| **OI-05** | **Confirmation whether bulk resource creation is included in Sprint 1 (Should vs Must).** Numbered sequence generation (e.g., Tables 1-20). | Resource API / UI | PROPOSED - Should requirement per PRD |
| **OI-06** | **Confirmation of API error codes and final error response contract.** Error structure, envelope fields, and status mapping. | Common backend, API contract | Contract ready, awaiting frontend lead confirmation |
| **OI-07** | **Agreement on team workstream assignments.** Allocation of 7 team members across the sprint work packages. | Sprint Management | PROPOSED - assignments confirmed in Phase 1 plan |

---

## 4. Phase 1 Architecture & Technical Proposals

| Item # | Technical Proposal | Proposed Specification | Rational & Reference |
|---|---|---|---|
| **TP-01** | REST Base Path | `/api/v1` | Standard versioned REST path for all platform endpoints. |
| **TP-02** | Standard Error Response Envelope | `{ "timestamp": "...", "status": 400, "code": "VALIDATION_FAILED", "message": "...", "path": "...", "traceId": "...", "details": [...] }` | Unified structure without revealing stack traces or internal class names. |
| **TP-03** | Location Scope Violation HTTP Status | `403 FORBIDDEN` with code `LOCATION_SCOPE_VIOLATION` | Distinguishes cross-tenant queries (404 RESOURCE_NOT_FOUND) from authenticated cross-location violations within permitted tenant. |
| **TP-04** | Staff JWT Claim Names & Values | `uid` (user id), `typ` (`STAFF_ACCESS`), `rol` (role enum), `tid` (tenant id), `oid` (org id), `lid` (location id), `did` (dept id), `jti` (JWT ID), `aud` (`venue-nexus-api`), `iss` (`venue-nexus-auth`) | Compact JWT standard claim representation. |
| **TP-05** | Password Hashing Standard | Argon2id (`memory=65536`, `iterations=3`, `parallelism=1`) as recommended choice; BCrypt (strength 12) as alternative | Argon2id is memory-hard and modern standard. |
| **TP-06** | Account Lockout Thresholds | 5 consecutive failed login attempts triggers a 15-minute temporary lockout | Mitigates brute-force credential stuffing. |
| **TP-07** | Platform-Level Audit Chain | Dedicated platform chain for `tenant_id IS NULL` system events (e.g., `TENANT_CREATED`) alongside per-tenant chains | Ensures platform admin actions are hash-chained without corrupting tenant chains. |
| **TP-08** | Trace ID Filter Safe Header Pattern | Regex: `^[A-Za-z0-9-]{8,64}$` | Prevents CRLF injection and log forgery while accepting standard UUIDs or custom IDs. |
| **TP-09** | Public QR Target URL Format | `${QR_BASE_URL}/q/{token}` where token is 22-char base64url | Unguessable URL format pointing to customer landing experience. |
| **TP-10** | Proposed Payment/Refund/AI Permissions | `PAYMENT_VIEW`, `REFUND_REQUEST`, `REFUND_APPROVE`, `AI_ADMIN` | Required for CR-02 and CR-03 governance; pending mentor approval. |
| **TP-11** | Audit Log Sequence Serialization | Database counter table (audit_chain) locked via SELECT ... FOR UPDATE in a single transaction; monotonic sequences without gaps or duplicates; unique index on audit_log as backstop | Implemented per Team Lead review note, counter table added to the Phase 2 review items, awaiting approval. |

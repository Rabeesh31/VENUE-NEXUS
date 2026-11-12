# VENUE NEXUS - Contract Approval & Sign-Off Matrix

**Document Title:** Contract Approval & Sign-Off Matrix  
**Version:** 1.0-APPROVED  
**Status:** APPROVED by Rabeesh R (Team Lead)  
**Owner:** Rithik Saran (Backend Lead)  
**Date:** 12 November 2026  

## Change Log
| Version | Date | Author | Description |
|---|---|---|---|
| 0.1-DRAFT | 6 November 2026 | Rithik Saran | Baseline draft of contract approval table and TRD 15.3 verification checklist. |
| 0.2-DRAFT | 8 November 2026 | Rithik Saran | Added Frontend Error Contract review document and sign-off criteria for Lackshana per Team Lead review note. |
| 1.0-APPROVED | 12 November 2026 | Rabeesh R | Formal Team Lead approval and sign-off for Sprint 1 Release v0.1.0. |

---

## 1. Document Sign-Off Table

| Document | File Path | Version | Author / Owner | Reviewer | Approval Status | Date |
|---|---|---|---|---|---|---|
| **System Architecture** | `docs/architecture.md` | 1.0 | Rithik Saran (Backend Lead) | Rabeesh R (Team Lead) | **APPROVED** | 12 Nov 2026 |
| **REST API Contract** | `docs/api-contract.md` | 1.0 | Rithik Saran (Backend Lead) | Rabeesh R (Team Lead) | **APPROVED** | 12 Nov 2026 |
| **Outbox Event Contract** | `docs/event-contract.md` | 1.0 | Rithik Saran (Backend Lead) | Rabeesh R (Team Lead) | **APPROVED** | 12 Nov 2026 |
| **Database Schema** | `docs/db-schema.md` | 1.0 | Rithik Saran & Kavisharmitha | Rabeesh R (Team Lead) | **APPROVED** | 12 Nov 2026 |
| **Security & Permissions** | `docs/security.md` | 1.0 | Rithik Saran (Backend Lead) | Rabeesh R (Team Lead) | **APPROVED** | 12 Nov 2026 |
| **Deployment & Ops** | `docs/deployment.md` | 1.0 | Rithik Saran (Backend Lead) | Rabeesh R (Team Lead) | **APPROVED** | 12 Nov 2026 |
| **Open Decisions Registry**| `docs/open-decisions.md` | 1.0 | Rithik Saran (Backend Lead) | Rabeesh R (Team Lead) | **APPROVED** | 12 Nov 2026 |
| **Frontend Error Review** | `docs/frontend-error-contract-review.md` | 1.0 | Rithik Saran (Backend Lead) | Rabeesh R (Team Lead) & Lackshana | **APPROVED** | 12 Nov 2026 |

---

## 2. TRD Section 15.3 Contract Approval Checklist

Before the Sprint 1 contract freeze can be finalized, Team Lead Rabeesh R and team leads must verify and sign off on each item below:

| # | Contract Item | Verification Criteria | Location in Contract Docs | Sign-Off Status |
|---|---|---|---|---|
| 1 | **Payment & Refund Status Enums** | Payment status enum (`PENDING`, `PROCESSING`, `SUCCEEDED`, `FAILED`, `CANCELLED`, `REFUNDED`) and Refund status enum (`REQUESTED`, `PROCESSING`, `SUCCEEDED`, `FAILED`, `REJECTED`) defined; payment status confirmed independent of operational order status (no competing order statuses). | `docs/api-contract.md` §7, `docs/architecture.md` §3.4 | [x] Approved |
| 2 | **Payment, Refund & Webhook Event Envelopes** | Standard outbox event envelopes documented for `PaymentSucceededEvent`, `PaymentFailedEvent`, `RefundRequestedEvent`, `RefundProcessedEvent`, and `PaymentWebhookReceivedEvent`. | `docs/event-contract.md` §4 | [x] Approved |
| 3 | **Customer Session Closure Contract** | Closure reasons defined: `CLOSED_BY_CUSTOMER` (CR-04), `CLOSED_AUTO_TRANSACTION_COMPLETE` (CR-05, proposed default D-4), and `CLOSED_AUTO_ORDER_PLACED` (CR-05). Enforces existing `CLOSED` session state (no new state invented). | `docs/api-contract.md` §8, `docs/event-contract.md` §5 | [x] Approved |
| 4 | **Automatic-Logout & Payment Mode Settings** | Configuration property names identified and reserved: `AUTO_LOGOUT_TRIGGER` and `PAYMENT_MODE_DEFAULT` (names only; physical DB columns deferred to post-V4 migrations). | `docs/security.md` §9, `docs/deployment.md` §4 | [x] Approved |
| 5 | **AI Governance & Feature Flags** | Advisory-only rule; feature flags defined per tenant/location (default OFF: `FEATURE_FLAG_AI_RECOMMENDATIONS_ENABLED`, `FEATURE_FLAG_AI_CHATBOT_ENABLED`, `FEATURE_FLAG_AI_ROUTING_ENABLED`); `AiProvider` interface abstraction; strict rule prohibiting tokens, OTP, PII, and credentials from reaching AI logs or providers. | `docs/security.md` §8 | [x] Approved |
| 6 | **Reserved Error Codes Catalogue** | Error codes added and marked RESERVED for payment (`PAYMENT_PROVIDER_ERROR`, `PAYMENT_DECLINED`), refund (`REFUND_NOT_PERMITTED`, `REFUND_WINDOW_EXPIRED`), webhook (`WEBHOOK_SIGNATURE_INVALID`), session-end (`SESSION_CANNOT_CLOSE`), and AI (`AI_SERVICE_UNAVAILABLE`). | `docs/api-contract.md` §4 | [x] Approved |
| 7 | **CR-01 Redis Client Wording** | Term "Lettuce" completely eliminated from `architecture.md` and `deployment.md`. Client referred to as "the Redis client" or "Spring Boot's default Redis client, see pom.xml". | `docs/architecture.md`, `docs/deployment.md` | [x] Approved |
| 8 | **Role Matrix Additions** | Proposed rows included for `PAYMENT_VIEW`, `REFUND_REQUEST`, `REFUND_APPROVE`, and `AI_ADMIN` with role assignments marked *Pending Team Lead confirmation* and mentor approval. | `docs/security.md` §2 | [x] Approved |
| 9 | **Reserved Audit Event Types** | Event types defined: `PAYMENT_SUCCEEDED`, `REFUND_REQUESTED`, `SESSION_ENDED`, `AI_FEATURE_USED` in audit catalog. | `docs/security.md` §6 | [x] Approved |
| 10 | **Frontend API Error Contract** | Standard 7-field error envelope (TP-02), worked examples (wrong login 401, validation 400, forbidden 403, not found 404, internal error 500), and frontend consumption rules documented in `docs/frontend-error-contract-review.md` with 4 confirmation questions for Lackshana. | `docs/frontend-error-contract-review.md`, `docs/api-contract.md` §3.4 | [x] Approved |

---

## 3. Formal Sign-Off Record

**Team Lead Sign-Off:** Rabeesh R  
**Date:** 12 November 2026  
**Notes:** Formally approved for Sprint 1 Release (v0.1.0). Core architecture, RBAC, API, and audit hash-chain requirements verified and tested. MinIO infrastructure and public QR customer resolution endpoints explicitly deferred to Sprint 2 scope.  

**Frontend Lead Sign-Off (Error Contract):** Lackshana / Confirmed via Phase 7 UI Integration  
**Date:** 12 November 2026  
**Notes:** Integrated with centralized apiClient error parsing, trace IDs, and toast notifications.  

# VENUE NEXUS - Contract Approval & Sign-Off Matrix

**Document Title:** Contract Approval & Sign-Off Matrix  
**Version:** 0.1-DRAFT  
**Status:** DRAFT - awaiting approval by Rabeesh R  
**Owner:** Rithik Saran (Backend Lead)  
**Date:** 6 November 2026  

## Change Log
| Version | Date | Author | Description |
|---|---|---|---|
| 0.1-DRAFT | 6 November 2026 | Rithik Saran | Baseline draft of contract approval table and TRD 15.3 verification checklist. |

---

## 1. Document Sign-Off Table

| Document | File Path | Version | Author / Owner | Reviewer | Approval Status | Date |
|---|---|---|---|---|---|---|
| **System Architecture** | `docs/architecture.md` | 0.1-DRAFT | Rithik Saran (Backend Lead) | Rabeesh R (Team Lead) | *PENDING APPROVAL* | — |
| **REST API Contract** | `docs/api-contract.md` | 0.1-DRAFT | Rithik Saran (Backend Lead) | Rabeesh R (Team Lead) | *PENDING APPROVAL* | — |
| **Outbox Event Contract** | `docs/event-contract.md` | 0.1-DRAFT | Rithik Saran (Backend Lead) | Rabeesh R (Team Lead) | *PENDING APPROVAL* | — |
| **Database Schema** | `docs/db-schema.md` | 0.1-DRAFT | Rithik Saran & Kavisharmitha | Rabeesh R (Team Lead) | *PENDING APPROVAL* | — |
| **Security & Permissions** | `docs/security.md` | 0.1-DRAFT | Rithik Saran (Backend Lead) | Rabeesh R (Team Lead) | *PENDING APPROVAL* | — |
| **Deployment & Ops** | `docs/deployment.md` | 0.1-DRAFT | Rithik Saran (Backend Lead) | Rabeesh R (Team Lead) | *PENDING APPROVAL* | — |
| **Open Decisions Registry**| `docs/open-decisions.md` | 0.1-DRAFT | Rithik Saran (Backend Lead) | Rabeesh R (Team Lead) | *PENDING APPROVAL* | — |

---

## 2. TRD Section 15.3 Contract Approval Checklist

Before the Sprint 1 contract freeze can be finalized, Team Lead Rabeesh R must verify and sign off on each item below:

| # | Contract Item | Verification Criteria | Location in Contract Docs | Sign-Off Status |
|---|---|---|---|---|
| 1 | **Payment & Refund Status Enums** | Payment status enum (`PENDING`, `PROCESSING`, `SUCCEEDED`, `FAILED`, `CANCELLED`, `REFUNDED`) and Refund status enum (`REQUESTED`, `PROCESSING`, `SUCCEEDED`, `FAILED`, `REJECTED`) defined; payment status confirmed independent of operational order status (no competing order statuses). | `docs/api-contract.md` §7, `docs/architecture.md` §3.4 | [ ] Pending |
| 2 | **Payment, Refund & Webhook Event Envelopes** | Standard outbox event envelopes documented for `PaymentSucceededEvent`, `PaymentFailedEvent`, `RefundRequestedEvent`, `RefundProcessedEvent`, and `PaymentWebhookReceivedEvent`. | `docs/event-contract.md` §4 | [ ] Pending |
| 3 | **Customer Session Closure Contract** | Closure reasons defined: `CLOSED_BY_CUSTOMER` (CR-04), `CLOSED_AUTO_TRANSACTION_COMPLETE` (CR-05, proposed default D-4), and `CLOSED_AUTO_ORDER_PLACED` (CR-05). Enforces existing `CLOSED` session state (no new state invented). | `docs/api-contract.md` §8, `docs/event-contract.md` §5 | [ ] Pending |
| 4 | **Automatic-Logout & Payment Mode Settings** | Configuration property names identified and reserved: `AUTO_LOGOUT_TRIGGER` and `PAYMENT_MODE_DEFAULT` (names only; physical DB columns deferred to post-V4 migrations). | `docs/security.md` §9, `docs/deployment.md` §4 | [ ] Pending |
| 5 | **AI Governance & Feature Flags** | Advisory-only rule; feature flags defined per tenant/location (default OFF: `FEATURE_FLAG_AI_RECOMMENDATIONS_ENABLED`, `FEATURE_FLAG_AI_CHATBOT_ENABLED`, `FEATURE_FLAG_AI_ROUTING_ENABLED`); `AiProvider` interface abstraction; strict rule prohibiting tokens, OTP, PII, and credentials from reaching AI logs or providers. | `docs/security.md` §8 | [ ] Pending |
| 6 | **Reserved Error Codes Catalogue** | Error codes added and marked RESERVED for payment (`PAYMENT_PROVIDER_ERROR`, `PAYMENT_DECLINED`), refund (`REFUND_NOT_PERMITTED`, `REFUND_WINDOW_EXPIRED`), webhook (`WEBHOOK_SIGNATURE_INVALID`), session-end (`SESSION_CANNOT_CLOSE`), and AI (`AI_SERVICE_UNAVAILABLE`). | `docs/api-contract.md` §4 | [ ] Pending |
| 7 | **CR-01 Redis Client Wording** | Term "Lettuce" completely eliminated from `architecture.md` and `deployment.md`. Client referred to as "the Redis client" or "Spring Boot's default Redis client, see pom.xml". | `docs/architecture.md`, `docs/deployment.md` | [ ] Pending |
| 8 | **Role Matrix Additions** | Proposed rows included for `PAYMENT_VIEW`, `REFUND_REQUEST`, `REFUND_APPROVE`, and `AI_ADMIN` with role assignments marked *Pending Team Lead confirmation* and mentor approval. | `docs/security.md` §2 | [ ] Pending |
| 9 | **Reserved Audit Event Types** | Event types defined: `PAYMENT_SUCCEEDED`, `REFUND_REQUESTED`, `SESSION_ENDED`, `AI_FEATURE_USED` in audit catalog. | `docs/security.md` §6 | [ ] Pending |

---

## 3. Formal Sign-Off Record
**Team Lead Sign-Off:** ___________________________  
**Date:** ___________________________  
**Notes:**  

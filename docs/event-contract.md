# VENUE NEXUS - Outbox Event Contract Specification

**Document Title:** Outbox Event Contract Specification  
**Version:** 0.1-DRAFT  
**Status:** DRAFT - awaiting approval by Rabeesh R  
**Owner:** Rithik Saran (Backend Lead)  
**Date:** 6 November 2026  

## Change Log
| Version | Date | Author | Description |
|---|---|---|---|
| 0.1-DRAFT | 6 November 2026 | Rithik Saran | Baseline draft of transactional outbox event envelope, Sprint 1 domain events, CR-02 payment/refund/webhook events, and CR-04/05 session closure contracts. |

---

## 1. Event Model & Outbox Pattern

VENUE NEXUS implements the **Transactional Outbox Pattern** to ensure reliable, at-least-once domain event publication without distributed 2-phase commit transactions.
- **Transactional Atomic Write:** When a business entity is updated, an outbox record is inserted into the `outbox_event` table within the **same local PostgreSQL transaction**.
- **Publication Guarantee:** At-least-once delivery.
- **Consumer Obligation:** Event consumers **must be idempotent** using `eventId` or business aggregate identifiers.
- **Alignment:** Directly aligns with the columns of `outbox_event` (Phase 2 / Kavisharmitha).

---

## 2. Standard Event Envelope Specification

Every domain event payload published via the outbox conforms strictly to this JSON schema:

```json
{
  "eventId": "018f45a2-3c4d-7a8e-9f10-123456789abc",
  "eventType": "QrRevokedEvent",
  "eventVersion": 1,
  "occurredAt": "2026-11-06T10:30:00.000Z",
  "tenantId": "018f4590-1a2b-7c3d-8e4f-567890abcdef",
  "locationId": "018f4591-2b3c-7d4e-8f5a-678901bcdef0",
  "traceId": "req-89765432-abcd-1234",
  "actor": {
    "type": "STAFF",
    "id": "018f4592-3c4d-7e5f-9a6b-789012cdef01"
  },
  "payload": {
    "resourceId": "018f4593-4d5e-7f6a-0b7c-890123def012",
    "resourceCode": "T12",
    "qrVersion": 1,
    "revocationReason": "Damaged QR plaque replaced"
  }
}
```

### Envelope Fields Specification

| Field Name | Type | Constraint | Description |
|---|---|---|---|
| `eventId` | UUID (v7 / v4) | NOT NULL | Unique event identifier for deduplication. |
| `eventType` | String | NOT NULL | Canonical event name (e.g., `QrIssuedEvent`). |
| `eventVersion` | Integer | NOT NULL | Event schema version (increments on breaking schema change). |
| `occurredAt` | ISO-8601 String | NOT NULL, UTC | Timestamp when event occurred in UTC. |
| `tenantId` | UUID | Nullable for Platform | Tenant context identifier (null for platform-scope events). |
| `locationId` | UUID | Nullable | Location context identifier where applicable. |
| `traceId` | String | NOT NULL | Request trace ID correlating event to HTTP origin. |
| `actor` | Object | NOT NULL | `{ "type": "STAFF" \| "SYSTEM" \| "CUSTOMER", "id": "..." }` |
| `payload` | Object | NOT NULL | Domain event-specific payload. |

---

## 3. Sprint 1 Active Event Types

The following domain events are active or proposed active for Sprint 1 foundations:

| Event Type | Aggregate Type | Triggering Action | Payload Highlights |
|---|---|---|---|
| `TenantCreatedEvent` | `Tenant` | Platform Admin creates tenant | `tenantId`, `name`, `status` |
| `TenantStatusChangedEvent` | `Tenant` | Tenant suspended or reactivated | `tenantId`, `oldStatus`, `newStatus` |
| `LocationCreatedEvent` | `Location` | Tenant Admin creates location | `locationId`, `tenantId`, `name`, `timeZone` |
| `StaffUserCreatedEvent` | `AppUser` | Admin creates staff account | `userId`, `email`, `role`, `tenantId`, `locationId` |
| `StaffLoggedInEvent` | `AppUser` | Successful staff login | `userId`, `tenantId`, `role`, `ipHash` |
| `StaffLoginFailedEvent` | `AppUser` | Failed login attempt | `email`, `tenantId`, `ipHash`, `attemptsCount` |
| `StaffLoggedOutEvent` | `AppUser` | Staff explicitly logs out | `userId`, `tenantId` |
| `ResourceCreatedEvent` | `Resource` | Venue Manager registers table/room | `resourceId`, `resourceCode`, `type`, `locationId` |
| `QrIssuedEvent` | `ResourceQr` | QR generated for resource | `resourceId`, `resourceCode`, `qrVersion` |
| `QrRevokedEvent` | `ResourceQr` | QR identity revoked | `resourceId`, `resourceCode`, `qrVersion` |
| `QrReissuedEvent` | `ResourceQr` | QR revoked and replaced atomically| `resourceId`, `oldQrVersion`, `newQrVersion` |

---

## 4. Reserved Payment & Refund Event Envelopes [CR-02] (Sprint 3 Design)

The following events are frozen in contract for the Sprint 3 Payment implementation:

### 4.1 `PaymentSucceededEvent` [CR-02]
```json
{
  "eventId": "018f45a2-3c4d-7a8e-9f10-123456789ab1",
  "eventType": "PaymentSucceededEvent",
  "eventVersion": 1,
  "occurredAt": "2026-11-06T10:35:00.000Z",
  "tenantId": "018f4590-1a2b-7c3d-8e4f-567890abcdef",
  "locationId": "018f4591-2b3c-7d4e-8f5a-678901bcdef0",
  "traceId": "req-pay-123456",
  "actor": {
    "type": "CUSTOMER",
    "id": "cust-sess-998877"
  },
  "payload": {
    "transactionId": "txn-018f45a2-3c4d",
    "orderId": "ord-018f45a2-3c4d",
    "providerName": "MOCK_GATEWAY",
    "providerReference": "ch_3Pxxxxxx",
    "amountMinorUnits": 4500,
    "currency": "INR",
    "status": "SUCCEEDED"
  }
}
```

### 4.2 `RefundProcessedEvent` [CR-02]
```json
{
  "eventId": "018f45a2-3c4d-7a8e-9f10-123456789ab2",
  "eventType": "RefundProcessedEvent",
  "eventVersion": 1,
  "occurredAt": "2026-11-06T10:40:00.000Z",
  "tenantId": "018f4590-1a2b-7c3d-8e4f-567890abcdef",
  "locationId": "018f4591-2b3c-7d4e-8f5a-678901bcdef0",
  "traceId": "req-ref-654321",
  "actor": {
    "type": "STAFF",
    "id": "018f4592-3c4d-7e5f-9a6b-789012cdef01"
  },
  "payload": {
    "refundId": "ref-018f45a2-3c4d",
    "transactionId": "txn-018f45a2-3c4d",
    "amountMinorUnits": 4500,
    "status": "SUCCEEDED",
    "reason": "Customer cancellation prior to prep"
  }
}
```

### 4.3 `PaymentWebhookReceivedEvent` [CR-02]
```json
{
  "eventId": "018f45a2-3c4d-7a8e-9f10-123456789ab3",
  "eventType": "PaymentWebhookReceivedEvent",
  "eventVersion": 1,
  "occurredAt": "2026-11-06T10:34:58.000Z",
  "tenantId": "018f4590-1a2b-7c3d-8e4f-567890abcdef",
  "locationId": null,
  "traceId": "whk-778899",
  "actor": {
    "type": "SYSTEM",
    "id": "WEBHOOK_INGRESS"
  },
  "payload": {
    "providerName": "MOCK_GATEWAY",
    "providerEventId": "evt_1Pxxxxxx",
    "providerEventType": "charge.succeeded"
  }
}
```

---

## 5. Reserved Customer Session Closure Event [CR-04 & CR-05] (Sprint 2 Design)

When a customer session transitions to `CLOSED`, this event is dispatched:

```json
{
  "eventId": "018f45a2-3c4d-7a8e-9f10-123456789ab4",
  "eventType": "CustomerSessionEndedEvent",
  "eventVersion": 1,
  "occurredAt": "2026-11-06T10:45:00.000Z",
  "tenantId": "018f4590-1a2b-7c3d-8e4f-567890abcdef",
  "locationId": "018f4591-2b3c-7d4e-8f5a-678901bcdef0",
  "traceId": "req-sess-end-1122",
  "actor": {
    "type": "CUSTOMER",
    "id": "cust-sess-998877"
  },
  "payload": {
    "sessionId": "sess-018f45a2-3c4d",
    "resourceId": "018f4593-4d5e-7f6a-0b7c-890123def012",
    "previousStatus": "ACTIVE",
    "closureReason": "CLOSED_BY_CUSTOMER"
  }
}
```

### Permitted Values for `closureReason`:
1. `CLOSED_BY_CUSTOMER`: Customer explicitly ended session via UI [CR-04].
2. `CLOSED_AUTO_TRANSACTION_COMPLETE`: Session closed automatically upon successful transaction settlement [CR-05, D-4].
3. `CLOSED_AUTO_ORDER_PLACED`: Session closed automatically once order was placed [CR-05].

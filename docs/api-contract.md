# VENUE NEXUS - REST API Contract Specification

**Document Title:** REST API Contract Specification  
**Version:** 0.2-DRAFT  
**Status:** DRAFT - awaiting approval by Rabeesh R  
**Owner:** Rithik Saran (Backend Lead)  
**Date:** 8 November 2026  

## Change Log
| Version | Date | Author | Description |
|---|---|---|---|
| 0.1-DRAFT | 6 November 2026 | Rithik Saran | Baseline draft of REST conventions, error envelope, pagination format, error code catalogue, idempotency rules, and CR-01..CR-05 contracts. |
| 0.2-DRAFT | 8 November 2026 | Rithik Saran | Added section 3.4 'Error contract agreed with frontend' with complete worked examples (wrong login, validation, 403, 404, 500) and frontend usage rules per Team Lead review note. |

---

## 1. General Principles & Transport Conventions

### 1.1 Base URL & Versioning [PROPOSED]
All platform endpoints are versioned through the URI path:
```text
https://api.venuenexus.io/api/v1
```
- **Base Path:** `/api/v1` *(PROPOSED - pending Team Lead confirmation)*.
- **Deprecation Policy:** Major breaking changes increment the URI path version (e.g., `/api/v2`). Deprecated endpoints return a `Sunset: <date>` and `Link: <url>; rel="deprecation"` HTTP header and remain active for at least 90 days.

### 1.2 URI Naming & JSON Formatting
- **Resource URIs:** Lowercase kebab-case using plural nouns (e.g., `/api/v1/locations`, `/api/v1/resources`, `/api/v1/space-nodes`).
- **Query Parameters:** Lowercase snake_case or camelCase (standardized to `camelCase` for consistency: `page`, `size`, `sortBy`, `sortDirection`).
- **JSON Request / Response Keys:** Strictly `camelCase` (e.g., `resourceCode`, `displayName`, `tenantId`).
- **HTTP Headers:** Standard kebab-case (e.g., `X-Trace-Id`, `Idempotency-Key`, `Authorization`).

### 1.3 Authentication Header
Protected endpoints require the standard Bearer authentication header:
```http
Authorization: Bearer <staff_access_jwt>
```
If the token is missing, expired, or malformed, the API responds with standard 401 or 403 error envelopes.

---

## 2. HTTP Verbs & Status Code Conventions

| HTTP Method | Usage | Success Status | Error Statuses |
|---|---|---|---|
| `GET` | Retrieve a resource or collection | `200 OK` | `400`, `401`, `403`, `404` |
| `POST` | Create a resource or execute an action | `201 Created` or `200 OK` | `400`, `401`, `403`, `409`, `422` |
| `PUT` | Full replacement of an existing resource | `200 OK` | `400`, `401`, `403`, `404`, `409` |
| `PATCH` | Partial update of an existing resource | `200 OK` | `400`, `401`, `403`, `404`, `409` |
| `DELETE` | Soft-delete or remove a resource | `204 No Content` | `400`, `401`, `403`, `404`, `409` |

### Tenant Isolation & 404 Concealment Rule
**CRITICAL:** When an authenticated tenant attempts to access, update, or delete a record belonging to another tenant, the API **must return HTTP 404 Not Found** with error code `RESOURCE_NOT_FOUND`. It must never return 401, 403, or 409, which would confirm to an attacker that the resource exists.

---

## 3. Standard Response Formats

### 3.1 Standard Success Envelope (Single Entity)
Direct resource representations are returned in the response root:
```json
{
  "id": "018f45a2-3c4d-7a8e-9f10-123456789abc",
  "resourceCode": "T12",
  "displayName": "Table 12",
  "type": "TABLE",
  "status": "AVAILABLE",
  "locationId": "018f4590-1a2b-7c3d-8e4f-567890abcdef",
  "createdAt": "2026-11-06T10:15:30Z"
}
```

### 3.2 Standard Pagination Contract
All collection endpoints support pagination parameters:
- `page`: 0-based page index (integer >= 0, default `0`).
- `size`: page size (integer, 1 <= size <= 100, default `20`).
- `sortBy`: whitelisted field name per endpoint.
- `sortDirection`: `ASC` or `DESC` (default `ASC`).
- Invalid pagination values return HTTP 400 with code `PAGINATION_INVALID`.

#### Paginated Response Envelope
```json
{
  "items": [
    {
      "id": "018f45a2-3c4d-7a8e-9f10-123456789abc",
      "resourceCode": "T01",
      "displayName": "Table 1",
      "status": "AVAILABLE"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 45,
  "totalPages": 3
}
```

### 3.3 Standard Error Envelope [PROPOSED]
Every error response from the platform adheres strictly to this format:
```json
{
  "timestamp": "2026-11-06T10:15:30.123Z",
  "status": 400,
  "code": "VALIDATION_FAILED",
  "message": "The request payload failed input validation constraints.",
  "path": "/api/v1/resources",
  "traceId": "c6a2b8e4-1f3d-4c5e-8a9b-7e6d5c4b3a21",
  "details": [
    {
      "field": "resourceCode",
      "issue": "must not be blank"
    }
  ]
}
```
- **Information Protection Rule:** The error payload must **never** include Java stack traces, SQL syntax snippets, internal package names, or database column names.
- **Traceability:** The `traceId` in the body matches the `X-Trace-Id` HTTP response header and server logs.

### 3.4 Error Contract Agreed with Frontend [PROPOSED]

This section establishes the shared error handling contract between the backend and the frontend applications (Staff Portal and Customer Web App) agreed per Team Lead review note.

#### 1. Common Response Format
The backend returns an identical JSON structure for all HTTP 4xx and 5xx errors:
- `timestamp` (ISO-8601 UTC string)
- `status` (integer HTTP status code matching HTTP header)
- `code` (standard platform error code string from Error Code Catalogue)
- `message` (human-readable summary message safe for display)
- `path` (request URI path)
- `traceId` (UUID string matching `X-Trace-Id` response header)
- `details` (array of field-level errors; empty array `[]` when no field-specific details exist)

#### 2. Worked Examples & Frontend Consumption Rules

##### a) Wrong Login (HTTP 401 Unauthorized)
Returned when authentication fails during staff login:
```json
{
  "timestamp": "2026-11-08T10:15:30.123Z",
  "status": 401,
  "code": "AUTH_INVALID_CREDENTIALS",
  "message": "Invalid login credentials",
  "path": "/api/v1/auth/login",
  "traceId": "c6a2b8e4-1f3d-4c5e-8a9b-7e6d5c4b3a21",
  "details": []
}
```
**Frontend Consumption Rules:**
- Inspect `response.data.code === "AUTH_INVALID_CREDENTIALS"`.
- Display `response.data.message` ("Invalid login credentials") directly on the login form (e.g. alert banner or below password input).
- **Security Requirement:** The message must **never** reveal whether the email exists in the database. Both wrong password and non-existent email return the exact same generic message to prevent user enumeration.

##### b) Validation Error (HTTP 400 Bad Request)
Returned when request parameters or payload body violate schema/validation constraints:
```json
{
  "timestamp": "2026-11-08T10:15:30.123Z",
  "status": 400,
  "code": "VALIDATION_FAILED",
  "message": "Validation failed for one or more fields.",
  "path": "/api/v1/resources",
  "traceId": "a1b2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d",
  "details": [
    {
      "field": "code",
      "issue": "must not be blank"
    },
    {
      "field": "capacity",
      "issue": "must be greater than 0"
    }
  ]
}
```
**Frontend Consumption Rules:**
- Iterate over the `details` array.
- Map each `detail.field` to the corresponding form input name.
- Render `detail.issue` inline beneath the corresponding input field.

##### c) Forbidden Access (HTTP 403 Forbidden)
Returned when user identity is authenticated but lacks required RBAC permission or location scope:
```json
{
  "timestamp": "2026-11-08T10:15:30.123Z",
  "status": 403,
  "code": "ACCESS_DENIED",
  "message": "Access is denied for this role and permission.",
  "path": "/api/v1/admin/tenants",
  "traceId": "f7e6d5c4-b3a2-1f0e-9d8c-7b6a5e4d3c2b",
  "details": []
}
```
**Frontend Consumption Rules:**
- Display an unauthorized access alert banner or navigate user to a restricted access notification page.
- Do not expose internal permission identifiers in end-user messaging.

##### d) Resource Not Found (HTTP 404 Not Found)
Returned when target resource does not exist or belongs to another tenant (concealed):
```json
{
  "timestamp": "2026-11-08T10:15:30.123Z",
  "status": 404,
  "code": "RESOURCE_NOT_FOUND",
  "message": "The requested resource could not be found.",
  "path": "/api/v1/resources/018f45a2-3c4d-7a8e-9f10-123456789abc",
  "traceId": "e5d4c3b2-a1f0-9e8d-7c6b-5a4e3d2c1b0a",
  "details": []
}
```
**Frontend Consumption Rules:**
- Display a "Resource not found" toast or empty state illustration.
- Remove deleted/missing entity from local state cache.

##### e) Internal Server Error (HTTP 500 Internal Server Error)
Returned on unexpected server-side runtime failure:
```json
{
  "timestamp": "2026-11-08T10:15:30.123Z",
  "status": 500,
  "code": "INTERNAL_ERROR",
  "message": "An internal server error occurred.",
  "path": "/api/v1/orders",
  "traceId": "9a8b7c6d-5e4f-3a2b-1c0d-e9f8a7b6c5d4",
  "details": []
}
```
**Frontend Consumption Rules:**
- Show a user-friendly generic toast: "Something went wrong. Please try again later."
- Display `traceId` with copy-to-clipboard functionality so users can quote the incident ID to technical support.
- Never expect or parse internal stack traces or database errors.

---

## 4. Error Code Catalogue

| Error Code | HTTP Status | Meaning | Safe for End Users? | Status in Sprint 1 |
|---|---|---|---|---|
| `VALIDATION_FAILED` | 400 | Request validation failed (e.g. blank field, invalid format) | Yes (field errors safe) | **ACTIVE** |
| `MALFORMED_REQUEST` | 400 | Unreadable JSON or mismatched data types | Yes | **ACTIVE** |
| `PAGINATION_INVALID` | 400 | Page < 0 or size outside range [1, 100] or invalid sort field | Yes | **ACTIVE** |
| `INVALID_PARENT_CHILD` | 400 | Space or entity parent does not belong to active location | Yes | **ACTIVE** |
| `AUTH_INVALID_CREDENTIALS` | 401 | Incorrect email or password (generic message) | Yes | **ACTIVE** |
| `AUTH_ACCOUNT_LOCKED` | 401 | Account locked due to repeated failed logins | Yes | **ACTIVE** |
| `AUTH_TOKEN_EXPIRED` | 401 | Access JWT has passed its expiration time | Yes | **ACTIVE** |
| `AUTH_TOKEN_INVALID` | 401 | JWT signature verification or decoding failed | Yes | **ACTIVE** |
| `AUTH_TOKEN_WRONG_TYPE` | 401 | Expected access token but received refresh or customer token | Yes | **ACTIVE** |
| `AUTH_TOKEN_INVALID_AUDIENCE` | 401 | Token audience does not match `venue-nexus-api` | Yes | **ACTIVE** |
| `AUTH_TOKEN_REVOKED` | 401 | Token identifier is present on the denylist | Yes | **ACTIVE** |
| `AUTH_REFRESH_REUSE_DETECTED` | 401 | Previously rotated refresh token presented; family revoked | Yes | **ACTIVE** |
| `ACCESS_DENIED` | 403 | User role does not possess required permission | Yes | **ACTIVE** |
| `LOCATION_SCOPE_VIOLATION` | 403 | User scope does not permit accessing this location [PROPOSED]| Yes | **ACTIVE** |
| `RESOURCE_NOT_FOUND` | 404 | Entity not found, or belongs to another tenant (concealed) | Yes | **ACTIVE** |
| `QR_NOT_FOUND` | 404 | Provided QR token hash does not match any active resource | Yes | **ACTIVE** |
| `METHOD_NOT_ALLOWED` | 405 | HTTP method not supported for endpoint | Yes | **ACTIVE** |
| `CONFLICT_DUPLICATE` | 409 | Unique constraint violated (e.g. duplicate resource code) | Yes | **ACTIVE** |
| `QR_REVOKED` | 410 | QR identity was previously revoked and cannot resolve | Yes | **ACTIVE** |
| `UNSUPPORTED_MEDIA_TYPE` | 415 | Content-Type is not application/json | Yes | **ACTIVE** |
| `INTERNAL_ERROR` | 500 | Unexpected internal server exception (generic response) | Yes | **ACTIVE** |
| `PAYMENT_PROVIDER_ERROR` [CR-02]| 502 | Upstream payment gateway failed or returned error | Yes | *RESERVED (Sprint 3)* |
| `PAYMENT_DECLINED` [CR-02] | 402 | Payment transaction declined by issuer or gateway | Yes | *RESERVED (Sprint 3)* |
| `REFUND_NOT_PERMITTED` [CR-02] | 400 | Transaction is not in a refundable state | Yes | *RESERVED (Sprint 3)* |
| `REFUND_WINDOW_EXPIRED` [CR-02]| 400 | Refund requested after permitted location refund window | Yes | *RESERVED (Sprint 3)* |
| `WEBHOOK_SIGNATURE_INVALID` [CR-02]| 401 | Payment provider webhook signature validation failed | No (audit only) | *RESERVED (Sprint 3)* |
| `SESSION_CANNOT_CLOSE` [CR-04] | 400 | Session in unclosable state (e.g. payment in flight) | Yes | *RESERVED (Sprint 2)* |
| `AI_SERVICE_UNAVAILABLE` [CR-03] | 503 | AI upstream provider timeout or error (advisory only) | Yes | *RESERVED (Sprint 2/3)* |
| `AI_REQUEST_REJECTED` [CR-03] | 400 | AI prompt policy rejection or feature flag disabled | Yes | *RESERVED (Sprint 2/3)* |

---

## 5. Distributed Tracing (`X-Trace-Id`) Specification

1. **Request Header:** Clients may supply an `X-Trace-Id` header.
2. **Validation Rule [PROPOSED]:**
   - Must match pattern: `^[A-Za-z0-9-]{8,64}$`
   - If missing, invalid, containing newlines/CRLF, or longer than 64 characters, the server discards it and generates a fresh UUIDv4.
3. **Response Header:** Every HTTP response includes `X-Trace-Id: <traceId>`.
4. **Log Correlation:** The trace ID is bound to the logging MDC, ensuring every log entry for the request includes `[traceId=<traceId>]`.

---

## 6. Idempotency Header Rules (`Idempotency-Key`)

To prevent duplicate execution of mutating operations (e.g., resource bulk creation, payment initiation [CR-02], QR issuance):
- **Header:** `Idempotency-Key: <unique_client_string>`
- **Storage:** Handled via the `idempotency_record` table (Phase 2 / Kavisharmitha).
- **Semantics:**
  - If a key is currently processing, concurrent requests with the same key receive `409 Conflict`.
  - Once processed, subsequent requests with the same key and payload hash receive the cached stored response without re-executing.
  - Keys expire after 24 hours.

---

## 7. Change Request Contracts (CR-02 to CR-05)

### 7.1 Payment & Refund Enums [CR-02] (Sprint 3 Design)
- **Payment Status Enum [PROPOSED]:**
  - `PENDING`: Transaction initiated, waiting for customer payment action.
  - `PROCESSING`: Payment submitted to gateway, awaiting webhook/settlement confirmation.
  - `SUCCEEDED`: Funds successfully authorized and captured.
  - `FAILED`: Payment rejected or gateway error.
  - `CANCELLED`: Payment session aborted by user or timeout.
  - `REFUNDED`: Full or partial refund settled.
- **Refund Status Enum [PROPOSED]:**
  - `REQUESTED`: Refund request submitted by staff or customer.
  - `PROCESSING`: Submitted to gateway.
  - `SUCCEEDED`: Refund settled.
  - `FAILED`: Gateway refund failure.
  - `REJECTED`: Supervisor or manager rejected the refund request.
- **Rule:** Payment status is completely decoupled from operational order status (e.g. `ORDER_SUBMITTED`, `IN_KITCHEN`, `READY_FOR_PICKUP`).

### 7.2 Customer Session Closure Contract [CR-04 & CR-05] (Sprint 2 Design)
Customer sessions utilize strictly the existing `CLOSED` state; no new session state is introduced.
- **Closure Reasons:**
  1. `CLOSED_BY_CUSTOMER` [CR-04]: Customer clicks "End Session" in portal while status is `PENDING` or `ACTIVE` (allowed even before ordering).
  2. `CLOSED_AUTO_TRANSACTION_COMPLETE` [CR-05]: Triggered automatically when transaction reaches `SUCCEEDED` (proposed default trigger, D-4).
  3. `CLOSED_AUTO_ORDER_PLACED` [CR-05]: Configurable trigger when an order is accepted.
- **Backend Authority:** The closure must be validated and enforced by backend services, not client-side timers.

---

## 8. Example Request and Response Payloads

### Example 1: Successful Paginated Resource Query
**Request:**
```http
GET /api/v1/resources?page=0&size=2 HTTP/1.1
Host: api.venuenexus.io
Authorization: Bearer <valid_token>
X-Trace-Id: req-98765432-abcd
```
**Response:**
```http
HTTP/1.1 200 OK
Content-Type: application/json
X-Trace-Id: req-98765432-abcd

{
  "items": [
    {
      "id": "018f45a2-3c4d-7a8e-9f10-123456789abc",
      "resourceCode": "T01",
      "displayName": "Table 1",
      "type": "TABLE",
      "status": "AVAILABLE"
    },
    {
      "id": "018f45a2-3c4d-7a8e-9f10-123456789abd",
      "resourceCode": "T02",
      "displayName": "Table 2",
      "type": "TABLE",
      "status": "AVAILABLE"
    }
  ],
  "page": 0,
  "size": 2,
  "totalElements": 24,
  "totalPages": 12
}
```

### Example 2: Cross-Tenant Concealment (404)
**Request:**
```http
GET /api/v1/resources/018f45a2-other-tenant-resource HTTP/1.1
Host: api.venuenexus.io
Authorization: Bearer <tenant_a_token>
```
**Response:**
```http
HTTP/1.1 404 Not Found
Content-Type: application/json
X-Trace-Id: c4b3a210-9f8e-4d5c-8b7a-6e5d4c3b2a10

{
  "timestamp": "2026-11-06T10:20:00.000Z",
  "status": 404,
  "code": "RESOURCE_NOT_FOUND",
  "message": "The requested resource could not be found.",
  "path": "/api/v1/resources/018f45a2-other-tenant-resource",
  "traceId": "c4b3a210-9f8e-4d5c-8b7a-6e5d4c3b2a10",
  "details": []
}
```

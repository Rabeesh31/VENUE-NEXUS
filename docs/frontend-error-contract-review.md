# VENUE NEXUS - Frontend API Error Contract Review

**Document Title:** Frontend API Error Contract Review & Handshake Specification  
**Version:** 0.1-DRAFT  
**Status:** DRAFT - awaiting confirmation by Lackshana (Frontend Lead)  
**Author:** Rithik Saran (Backend Lead)  
**Reviewer:** Lackshana (Frontend Lead)  
**Date:** 8 November 2026  

---

## 1. Overview & Context

During the Phase 1 architectural review, Team Lead Rabeesh R issued the following instruction (OI-06):
> *"Define a common error response format in the backend. Example: if someone gives wrong login details, the backend sends an error response, and the frontend receives it and shows 'invalid login credentials'. Discuss with the frontend lead so they follow the same error response. Nothing else to change."*

This document presents the standardized error envelope implemented across all backend services, provides the concrete worked examples, and establishes the formal review and sign-off criteria for Lackshana prior to Phase 6 frontend integration.

---

## 2. Standardized JSON Error Response Shape

Every error response returned by the VENUE NEXUS REST API adheres strictly to the 7-field contract envelope (TP-02):

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

### Envelope Field Definitions

| Field Name | Type | Description |
|---|---|---|
| `timestamp` | String (ISO-8601 UTC) | Exact UTC timestamp when the server generated the error response. |
| `status` | Integer | HTTP status code matching the HTTP response status header. |
| `code` | String | Machine-readable error enum string from the platform Error Code Catalogue. |
| `message` | String | Safe, human-readable description appropriate for display or logging. |
| `path` | String | Request URI that triggered the error condition. |
| `traceId` | String (UUID) | Unique distributed trace ID matching the `X-Trace-Id` HTTP response header. |
| `details` | Array of Objects | Field-specific issues (`field`, `issue`); guaranteed empty `[]` when no field errors exist. |

---

## 3. Worked Examples

### 3.1 Wrong Staff Login (HTTP 401 Unauthorized)

When authentication fails on `/api/v1/auth/login`:
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
**Frontend Handling:**
- The frontend inspects `response.status === 401` and `response.data.code === "AUTH_INVALID_CREDENTIALS"`.
- Displays `response.data.message` ("Invalid login credentials") at the top of the login credentials form.
- **Security Rule:** To prevent email enumeration attacks, this identical payload is returned whether the email does not exist or the password is incorrect.

### 3.2 Form Validation Failure (HTTP 400 Bad Request)

When a form submission violates input constraints on `/api/v1/resources`:
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
**Frontend Handling:**
- The frontend iterates over `details`.
- Maps each `detail.field` to the corresponding input name and renders `detail.issue` inline.

### 3.3 Access Forbidden (HTTP 403 Forbidden)

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

### 3.4 Entity Not Found (HTTP 404 Not Found)

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

### 3.5 Internal Server Error (HTTP 500 Internal Server Error)

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
**Frontend Handling:**
- Shows toast: *"Something went wrong. Please try again later."*
- Displays `traceId` with copy button so user can quote the reference ID to venue support.

---

## 4. Confirmation Questions for Lackshana (Frontend Lead)

Please review the following 4 points and record your confirmation:

1. **Envelope Shape Compatibility:**  
   Does the 7-field structure (`timestamp`, `status`, `code`, `message`, `path`, `traceId`, `details`) fit cleanly into the planned Axios / TanStack Query response interceptors for the Staff Portal and Customer Web App?
2. **Field Names in Details Array:**  
   Do the backend property names in the `details` list (e.g. `code`, `name`, `capacity`, `email`, `role`, `status`) directly align with the planned frontend form field state names?
3. **Display Text Ownership per Error Code:**  
   Who owns the end-user visible display copy?  
   - *Option A:* The frontend directly displays the backend-provided `message` (safe, pre-sanitized).  
   - *Option B:* The frontend switches on `code` and provides localized/customized UI strings, using `message` only as fallback.
4. **Generic Authentication Message Security:**  
   Does the frontend team confirm acceptance of the security requirement that both wrong password and non-existent email return the exact generic message `"Invalid login credentials"` (avoiding user enumeration)?

---

## 5. Frontend Lead Sign-Off Record

**Frontend Lead:** Lackshana  
**Sign-Off Status:** [ ] CONFIRMED & ACCEPTED / [ ] REVISIONS REQUESTED  
**Date:** ___________________________  
**Notes / Comments:**  

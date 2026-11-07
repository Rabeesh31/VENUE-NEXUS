# VENUE NEXUS — Authentication & Staff Security API Contract

## 1. Overview
All Phase 3 staff security endpoints are prefixed with `/api/v1/auth`. All requests and responses use `application/json` with UTF-8 encoding. Every response exposes the `X-Trace-Id` response header for distributed tracing.

---

## 2. Endpoints

### 2.1 Login
- **URL:** `POST /api/v1/auth/login`
- **Access:** Public (`permitAll`)
- **Headers:** `Content-Type: application/json`
- **Request Body:**
```json
{
  "email": "staff@venuenexus.com",
  "password": "SecretPassword123!"
}
```
- **Success Response (200 OK):**
```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "refreshToken": "dGhpcy1pcy1hbi1vcGFxdWUtcmVmcmVzaC10b2tlbg",
  "tokenType": "Bearer",
  "expiresIn": 900,
  "user": {
    "id": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
    "displayName": "Staff Name",
    "role": "STAFF",
    "tenantId": "c1f71df6-444c-41ad-a7f4-5f80b91e70d4",
    "organisationId": "e2a3b4c5-d6e7-48a9-b0c1-d2e3f4a5b6c7",
    "locationId": "f3b4c5d6-e7f8-49ba-b1c2-d3e4f5a6b7c8",
    "departmentId": null
  }
}
```
- **Error Responses:**
  - `400 BAD_REQUEST`: Blank email or password (`VALIDATION_ERROR`).
  - `401 UNAUTHORIZED`: Wrong password or unknown email (`AUTH_INVALID_CREDENTIALS`).
  - `423 LOCKED`: Account currently locked after 5 failed attempts (`AUTH_ACCOUNT_LOCKED`).

---

### 2.2 Refresh Token Rotation
- **URL:** `POST /api/v1/auth/refresh`
- **Access:** Public (`permitAll`)
- **Headers:** `Content-Type: application/json`
- **Request Body:**
```json
{
  "refreshToken": "dGhpcy1pcy1hbi1vcGFxdWUtcmVmcmVzaC10b2tlbg"
}
```
- **Success Response (200 OK):** Returns fresh `accessToken` and **new** `refreshToken` in the same family.
```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "refreshToken": "bmV3LW9wYXF1ZS1yZWZyZXNoLXRva2VuLXN0cmluZw",
  "tokenType": "Bearer",
  "expiresIn": 900,
  "user": { ... }
}
```
- **Error Responses:**
  - `400 BAD_REQUEST`: Missing refresh token (`VALIDATION_ERROR`).
  - `401 UNAUTHORIZED`: Unknown or malformed token (`AUTH_TOKEN_INVALID`), expired token (`AUTH_TOKEN_EXPIRED`), or reused rotated token (`AUTH_REFRESH_REUSE_DETECTED` - revokes entire family).

---

### 2.3 Logout
- **URL:** `POST /api/v1/auth/logout`
- **Access:** Authenticated (`Bearer <token>`)
- **Headers:**
  - `Authorization: Bearer <access_token>`
  - `Content-Type: application/json`
- **Request Body:** (Optional)
```json
{
  "refreshToken": "bmV3LW9wYXF1ZS1yZWZyZXNoLXRva2VuLXN0cmluZw"
}
```
- **Success Response (204 No Content):** Empty body. The current access token `jti` is denylisted in Redis with TTL equal to its remaining lifespan, and the supplied refresh token family is revoked.

---

## 3. Standard Error Response Schema

All errors emitted by the filter chain (401, 403) and MVC controllers share the identical JSON schema:

```json
{
  "timestamp": "2026-11-07T12:00:00Z",
  "status": 401,
  "error": "AUTH_INVALID_CREDENTIALS",
  "message": "Invalid email or password",
  "path": "/api/v1/auth/login",
  "traceId": "trace_7b8a9c0d1e2f"
}
```

### Phase 3 Error Code Mapping:
| ErrorCode | HTTP Status | Description |
|---|---|---|
| `VALIDATION_ERROR` | 400 | Request validation failed or mandatory fields missing |
| `UNAUTHENTICATED` | 401 | Missing authentication header |
| `AUTH_INVALID_CREDENTIALS` | 401 | Invalid email or password |
| `AUTH_TOKEN_EXPIRED` | 401 | JWT or refresh token has expired |
| `AUTH_TOKEN_INVALID` | 401 | Token signature, format, or claims invalid |
| `AUTH_TOKEN_WRONG_TYPE` | 401 | Token `typ` claim is not STAFF |
| `AUTH_TOKEN_INVALID_AUDIENCE` | 401 | Token audience or issuer does not match configuration |
| `AUTH_TOKEN_REVOKED` | 401 | Access token JTI found in denylist or Redis outage fail-closed |
| `AUTH_REFRESH_REUSE_DETECTED` | 401 | Revoked refresh token presented; entire family revoked |
| `ACCESS_DENIED` | 403 | Caller role lacks required privilege |
| `NOT_FOUND` | 404 | Resource not found, or out of caller's tenant/location scope |
| `AUTH_ACCOUNT_LOCKED` | 423 | Consecutive failed logins exceeded threshold |
| `INTERNAL_SERVER_ERROR` | 500 | Unhandled internal server error |


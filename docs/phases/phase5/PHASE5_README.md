# Phase 5: Resource registry, QR service, backend tests (Monisha)

Package: `com.venuenexus.resource` (module `resource` in the TRD). Layering: Controller -> Service -> Repository.

## Endpoints (`/api/v1/resources`)
| Method | Path | Roles (DRAFT matrix) | Notes |
|---|---|---|---|
| POST | `/` | TENANT_ADMIN, VENUE_MANAGER | 201; 409 `RESOURCE_CODE_EXISTS` |
| POST | `/bulk` | same | Should item; all-or-nothing; max 100; no QR issued |
| GET | `/` | + SUPERVISOR, STAFF | paged, max size 100, sort whitelist |
| GET | `/{id}` | read roles | 404 for other tenant/location |
| GET | `/{id}/qr` | read roles | status only, never a token |
| POST | `/{id}/qr` | manage roles | returns token URL + SVG + PNG ONCE; 409 `QR_ALREADY_ACTIVE` |
| POST | `/{id}/qr/revoke` | manage roles | 409 `QR_NOT_ACTIVE`; audits `QR_REVOKED` |
| POST | `/{id}/qr/reissue` | manage roles | revoke + issue in one tx |

## Ports you must get wired by other phases (`port/Ports.java`)
- `CallerContext` <- Phase 3 (tenantId, userId, allowedLocationIds from the JWT)
- `VenueLookup` <- Phase 4 (location/space/department ownership checks)
- `AuditPort` <- Phase 1 (`AuditLogService`); `ResourceApiException` -> map in `GlobalExceptionHandler`

## Needs from Phase 2 (V3) - must match the entities
- `resource(id, tenant_id, location_id, space_id, department_id, code, display_name, resource_type, status, activation_policy, capabilities jsonb, created_at, version)`
- UNIQUE `(tenant_id, location_id, code)`; codes are stored UPPER-CASE by the service
- `resource_qr(id, tenant_id, resource_id, token_hash varchar(64), version, status, issued_by, issued_at, revoked_at)`
- `CREATE UNIQUE INDEX ... ON resource_qr(resource_id) WHERE status = 'ACTIVE'`
- Token hash format: lower-case hex SHA-256 (64 chars)

## Config
- `PUBLIC_BASE_URL` (env), `venuenexus.qr.path-prefix` (default `/q/`, to be frozen in the QR contract)
- `spring.jackson.deserialization.fail-on-unknown-properties=true`
- Dependency to add: `com.google.zxing:core` (only core is used; drawing is plain JDK)
- New error codes to reserve with Phase 1: `RESOURCE_CODE_EXISTS`, `QR_ALREADY_ACTIVE`, `QR_NOT_ACTIVE`
- Audit events used: `QR_REVOKED` (required), `QR_ISSUED` (extra, confirm with Rithik)

## Security properties implemented
- 128-bit SecureRandom token, base64url, 22 chars; only SHA-256 stored; no token/hash getter or toString
- Token returned only in issue/reissue response with `Cache-Control: no-store`; image cannot be re-rendered later (reissue instead)
- Row lock on the resource serialises issue/revoke/reissue; partial unique index is the last guard
- Other-tenant / other-location ids always 404; tenantId never accepted from the client
- `QrService.validate(token)` = internal "resolves?" check (public endpoint is Sprint 2)

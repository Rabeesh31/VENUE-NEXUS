# VENUE NEXUS — Phase 1 & Phase 2 Integration Stubs (Stand-ins)

This document catalogs every stand-in class created in package `com.venuenexus.integration.stubs` to allow Phase 3 to compile and execute standalone.
When Phase 1 and Phase 2 are integrated, these stubs will be replaced by the official upstream classes without requiring changes to Phase 3 business logic.

---

## 1. Phase 1 Stubs (Common Errors, Tracing & Audit)

### 1.1 `ErrorCode`
- **Package:** `com.venuenexus.integration.stubs`
- **Class/Enum:** `public enum ErrorCode`
- **Key Methods & Signatures:**
  - `HttpStatus getHttpStatus()`
  - `String getDefaultMessage()`
- **Values Required by Phase 3:**
  - `AUTH_INVALID_CREDENTIALS` (401 UNAUTHORIZED)
  - `AUTH_ACCOUNT_LOCKED` (423 LOCKED)
  - `AUTH_TOKEN_EXPIRED` (401 UNAUTHORIZED)
  - `AUTH_TOKEN_INVALID` (401 UNAUTHORIZED)
  - `AUTH_TOKEN_WRONG_TYPE` (401 UNAUTHORIZED)
  - `AUTH_TOKEN_INVALID_AUDIENCE` (401 UNAUTHORIZED)
  - `AUTH_TOKEN_REVOKED` (401 UNAUTHORIZED)
  - `AUTH_REFRESH_REUSE_DETECTED` (401 UNAUTHORIZED)
  - `ACCESS_DENIED` (403 FORBIDDEN)
  - `LOCATION_SCOPE_VIOLATION` (403 FORBIDDEN)
  - `VALIDATION_ERROR` (400 BAD_REQUEST)
  - `UNAUTHENTICATED` (401 UNAUTHORIZED)
  - `FORBIDDEN` (403 FORBIDDEN)
  - `NOT_FOUND` (404 NOT_FOUND)
  - `INTERNAL_SERVER_ERROR` (500 INTERNAL_SERVER_ERROR)

### 1.2 `ApiException`
- **Package:** `com.venuenexus.integration.stubs`
- **Class:** `public class ApiException extends RuntimeException`
- **Constructors:**
  - `ApiException(ErrorCode errorCode)`
  - `ApiException(ErrorCode errorCode, String message)`
  - `ApiException(ErrorCode errorCode, String message, Throwable cause)`
- **Methods:**
  - `ErrorCode getErrorCode()`
  - `HttpStatus getHttpStatus()`

### 1.3 `ApiError`
- **Package:** `com.venuenexus.integration.stubs`
- **Class:** `public record ApiError(Instant timestamp, int status, String error, String message, String path, String traceId)`
- **Contract:** Represents the uniform JSON error response. The same JSON structure is produced by `GlobalExceptionHandler` and filter-level `AuthenticationEntryPoint` / `AccessDeniedHandler`.

### 1.4 `GlobalExceptionHandler`
- **Package:** `com.venuenexus.integration.stubs`
- **Class:** `@RestControllerAdvice public class GlobalExceptionHandler`
- **Handles:** `ApiException`, `MethodArgumentNotValidException`, `AccessDeniedException`, `Exception`
- **Output:** `ResponseEntity<ApiError>` with `X-Trace-Id` response header.

### 1.5 `TraceIdFilter`
- **Package:** `com.venuenexus.integration.stubs`
- **Class:** `public class TraceIdFilter extends OncePerRequestFilter`
- **Static Accessor:**
  - `public static String getCurrentTraceId()` (retrieves current trace ID from MDC or ThreadLocal)
- **Header:** Attaches `X-Trace-Id` header to every response and runs at `Ordered.HIGHEST_PRECEDENCE`.

### 1.6 `AuditLogService`
- **Package:** `com.venuenexus.integration.stubs`
- **Interface:** `public interface AuditLogService`
- **Method:**
  - `void record(AuditEvent event);`

### 1.7 `AuditEvent`
- **Package:** `com.venuenexus.integration.stubs`
- **Class:** `public record AuditEvent(UUID tenantId, UUID userId, AuditEventType eventType, boolean success, String details, Instant timestamp, String ipHash)`
- **Factory Methods:**
  - `static AuditEvent success(UUID tenantId, UUID userId, AuditEventType eventType, String details)`
  - `static AuditEvent failure(UUID tenantId, UUID userId, AuditEventType eventType, String details)`

### 1.8 `AuditEventType`
- **Package:** `com.venuenexus.integration.stubs`
- **Enum:** `public enum AuditEventType`
- **Values:**
  - `AUTH_LOGIN`, `AUTH_LOGIN_FAILED`, `AUTH_LOCKOUT`, `AUTH_REFRESH_REUSE_DETECTED`, `AUTH_TOKEN_REJECTED`, `ACCESS_DENIED_ROLE`, `ACCESS_DENIED_TENANT`, `ACCESS_DENIED_SCOPE`

---

## 2. Phase 2 Stubs (Database V2 Entities & Repositories)

### 2.1 `StaffRole`
- **Package:** `com.venuenexus.integration.stubs`
- **Enum:** `public enum StaffRole`
- **Values:**
  - `PLATFORM_ADMIN`, `TENANT_ADMIN`, `VENUE_MANAGER`, `SUPERVISOR`, `STAFF`

### 2.2 `UserStatus`
- **Package:** `com.venuenexus.integration.stubs`
- **Enum:** `public enum UserStatus`
- **Values:**
  - `ACTIVE`, `INACTIVE`, `SUSPENDED`

### 2.3 `AppUser`
- **Package:** `com.venuenexus.integration.stubs`
- **Entity:** `@Entity @Table(name = "app_user") public class AppUser`
- **Columns:**
  - `id` (UUID, PK)
  - `tenantId` (UUID, nullable for PLATFORM_ADMIN)
  - `organisationId` (UUID, nullable)
  - `locationId` (UUID, nullable)
  - `departmentId` (UUID, nullable)
  - `email` (String, unique case-insensitive)
  - `passwordHash` (String)
  - `displayName` (String)
  - `role` (StaffRole)
  - `status` (UserStatus)
  - `failedLoginAttempts` (int)
  - `lockedUntil` (Instant, nullable)
  - `lastLoginAt` (Instant, nullable)
  - `createdAt` (Instant)
  - `updatedAt` (Instant)

### 2.4 `RefreshToken`
- **Package:** `com.venuenexus.integration.stubs`
- **Entity:** `@Entity @Table(name = "refresh_token") public class RefreshToken`
- **Columns:**
  - `id` (UUID, PK)
  - `tokenHash` (String, unique SHA-256 hash)
  - `familyId` (UUID, indexed)
  - `userId` (UUID, FK to app_user)
  - `tenantId` (UUID, nullable for PLATFORM_ADMIN)
  - `expiresAt` (Instant)
  - `revokedAt` (Instant, nullable)
  - `replacedByTokenHash` (String, nullable)
  - `createdAt` (Instant)

### 2.5 `AppUserRepository`
- **Package:** `com.venuenexus.integration.stubs`
- **Interface:** `public interface AppUserRepository extends JpaRepository<AppUser, UUID>`
- **Sanctioned Pre-login Query:**
  - `Optional<AppUser> findForAuthenticationByEmail(String email);`
- **Tenant Scoped Queries:**
  - `Optional<AppUser> findByIdAndTenantId(UUID id, UUID tenantId);`

### 2.6 `RefreshTokenRepository`
- **Package:** `com.venuenexus.integration.stubs`
- **Interface:** `public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID>`
- **Sanctioned Queries:**
  - `Optional<RefreshToken> findByTokenHash(String tokenHash);`
  - `List<RefreshToken> findByFamilyId(UUID familyId);`
  - `@Modifying @Query void revokeFamily(UUID familyId, Instant revokedAt);`


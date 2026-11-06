package com.venuenexus.common.web;

import com.venuenexus.common.error.ApiException;
import com.venuenexus.common.error.ErrorCode;
import com.venuenexus.common.filter.TraceIdFilter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Test-scoped controller for exercising GlobalExceptionHandler and TraceIdFilter with MockMvc.
 */
@RestController
@RequestMapping("/test")
public class TestErrorController {

    @GetMapping("/api-exception")
    public void throwApiException() {
        throw new ApiException(ErrorCode.CONFLICT_DUPLICATE, "Duplicate resource code detected.");
    }

    @PostMapping("/validation")
    public ResponseEntity<String> testValidation(@Valid @RequestBody TestRequest request) {
        return ResponseEntity.ok("Valid: " + request.name());
    }

    @GetMapping("/type-mismatch")
    public ResponseEntity<String> testTypeMismatch(@RequestParam("page") int page) {
        return ResponseEntity.ok("Page: " + page);
    }

    @GetMapping("/missing-param")
    public ResponseEntity<String> testMissingParam(@RequestParam("requiredParam") String requiredParam) {
        return ResponseEntity.ok("Param: " + requiredParam);
    }

    @GetMapping("/access-denied")
    public void throwAccessDenied() {
        throw new AccessDeniedException("User role does not possess permission.");
    }

    @GetMapping("/auth-failure")
    public void throwAuthenticationException() {
        throw new BadCredentialsException("Invalid credentials provided.");
    }

    @GetMapping("/unexpected")
    public void throwUnexpected() {
        throw new RuntimeException("Unexpected internal failure simulation.");
    }

    @GetMapping("/echo-trace")
    public ResponseEntity<Map<String, String>> echoTrace() {
        return ResponseEntity.ok(Map.of("traceId", TraceIdFilter.getCurrentTraceId()));
    }

    public record TestRequest(
            @NotBlank(message = "must not be blank")
            @Size(min = 3, max = 20, message = "must be between 3 and 20 characters")
            String name
    ) {
    }
}

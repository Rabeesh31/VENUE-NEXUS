package com.venuenexus.identity.api;

import com.venuenexus.identity.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller exposing authentication endpoints for staff members.
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final com.venuenexus.identity.service.RefreshTokenService refreshTokenService;
    private final com.venuenexus.identity.service.LogoutService logoutService;

    public AuthController(
            AuthService authService,
            com.venuenexus.identity.service.RefreshTokenService refreshTokenService,
            com.venuenexus.identity.service.LogoutService logoutService) {
        this.authService = authService;
        this.refreshTokenService = refreshTokenService;
        this.logoutService = logoutService;
    }

    /**
     * Authenticates staff using email and password.
     * Returns an access JWT and an opaque refresh token.
     */
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        LoginResponse response = authService.login(request);
        return ResponseEntity.ok(response);
    }

    /**
     * Rotates a valid refresh token and issues a new access token and refresh token pair.
     */
    @PostMapping("/refresh")
    public ResponseEntity<LoginResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        LoginResponse response = refreshTokenService.rotate(request.refreshToken());
        return ResponseEntity.ok(response);
    }

    /**
     * Logs out the authenticated caller, revoking their refresh family and denylisting the access token JTI.
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestBody(required = false) LogoutRequest request) {
        com.venuenexus.security.context.CurrentStaff staff = com.venuenexus.security.context.CurrentStaff.current().orElse(null);
        if (staff != null) {
            logoutService.logout(staff, request != null ? request.refreshToken() : null);
        }
        return ResponseEntity.noContent().build();
    }
}


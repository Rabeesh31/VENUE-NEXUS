package com.venuenexus.security;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.venuenexus.common.error.ApiException;
import com.venuenexus.identity.api.LoginRequest;
import com.venuenexus.identity.api.LoginResponse;
import com.venuenexus.identity.entity.AppUser;
import com.venuenexus.identity.model.UserStatus;
import com.venuenexus.identity.repository.AppUserRepository;
import com.venuenexus.identity.service.AuthService;
import com.venuenexus.identity.service.LogoutService;
import com.venuenexus.identity.service.RefreshTokenService;
import com.venuenexus.security.StaffRole;
import com.venuenexus.security.context.CurrentStaff;
import com.venuenexus.security.denylist.TokenDenylist;
import com.venuenexus.VenueNexusApplication;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Log hygiene verification test (T-72) ensuring that no sensitive credentials,
 * plaintext passwords, BCrypt hashes, raw access/refresh tokens, or signing
 * keys
 * leak into log streams across login, refresh, logout, and lockout operations.
 */
@SpringBootTest(classes = VenueNexusApplication.class)
class LogHygieneTest {

    @Autowired
    private AuthService authService;

    @Autowired
    private RefreshTokenService refreshTokenService;

    @Autowired
    private LogoutService logoutService;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockBean
    private TokenDenylist tokenDenylist;

    @Value("${security.jwt.staff-secret}")
    private String jwtSecret;

    private ListAppender<ILoggingEvent> listAppender;
    private Logger rootLogger;

    private static final String PLAINTEXT_PASSWORD = "VeryConfidentialStaffPassword!2026";

    @BeforeEach
    void setUp() {
        when(tokenDenylist.isDenylisted(anyString())).thenReturn(false);

        rootLogger = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        listAppender = new ListAppender<>();
        listAppender.start();
        rootLogger.addAppender(listAppender);
    }

    @AfterEach
    void tearDown() {
        if (rootLogger != null && listAppender != null) {
            rootLogger.detachAppender(listAppender);
        }
    }

    @Test
    void testLogHygiene_AcrossAllAuthFlows_ContainsNoSecrets_T72() {
        UUID userId = UUID.randomUUID();
        String email = "hygiene.staff@venuenexus.com";
        String passwordHash = passwordEncoder.encode(PLAINTEXT_PASSWORD);

        AppUser user = AppUser.builder()
                .email(email)
                .passwordHash(passwordHash)
                .role(StaffRole.PLATFORM_ADMIN.name())
                .displayName("Hygiene Staff")
                .status(UserStatus.ACTIVE.name())
                .available(true)
                .build();
        user.setId(userId);
        appUserRepository.save(user);

        // 1. Successful Login
        LoginRequest loginRequest = new LoginRequest(email, PLAINTEXT_PASSWORD);
        LoginResponse loginResponse = authService.login(loginRequest);
        String accessToken = loginResponse.accessToken();
        String refreshToken = loginResponse.refreshToken();

        // 2. Failed Login (wrong password)
        try {
            authService.login(new LoginRequest(email, "WrongPasswordAttempt123!"));
        } catch (ApiException ignored) {
        }

        // 3. Failed Login (unknown email)
        try {
            authService.login(new LoginRequest("nonexistent.user@venuenexus.com", PLAINTEXT_PASSWORD));
        } catch (ApiException ignored) {
        }

        // 4. Token Refresh
        LoginResponse refreshResponse = refreshTokenService.rotate(refreshToken);
        String newRefreshToken = refreshResponse.refreshToken();

        // 5. Logout
        CurrentStaff currentStaff = new CurrentStaff(userId, StaffRole.PLATFORM_ADMIN, null, null, null, null,
                UUID.randomUUID().toString());
        logoutService.logout(currentStaff, newRefreshToken);

        // Collect all logged messages
        String combinedLogs = listAppender.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .collect(Collectors.joining("\n"));

        // Assert strictly that NO secrets or sensitive credentials appear anywhere in
        // log output
        assertThat(combinedLogs)
                .as("Logs must never contain plaintext user passwords")
                .doesNotContain(PLAINTEXT_PASSWORD)
                .doesNotContain("WrongPasswordAttempt123!");

        assertThat(combinedLogs)
                .as("Logs must never contain the JWT signing secret")
                .doesNotContain(jwtSecret);

        assertThat(combinedLogs)
                .as("Logs must never contain the BCrypt password hash")
                .doesNotContain(passwordHash)
                .doesNotContain("$2a$")
                .doesNotContain("$2b$");

        assertThat(combinedLogs)
                .as("Logs must never contain raw access tokens")
                .doesNotContain(accessToken);

        assertThat(combinedLogs)
                .as("Logs must never contain raw refresh tokens")
                .doesNotContain(refreshToken)
                .doesNotContain(newRefreshToken);
    }
}

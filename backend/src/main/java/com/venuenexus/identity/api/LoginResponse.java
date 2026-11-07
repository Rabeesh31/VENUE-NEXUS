package com.venuenexus.identity.api;

public record LoginResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn,
        UserSummaryDto user
) {
    public static LoginResponse of(String accessToken, String refreshToken, long expiresIn, UserSummaryDto user) {
        return new LoginResponse(accessToken, refreshToken, "Bearer", expiresIn, user);
    }
}


package com.venuenexus.identity.api;

public record LogoutRequest(
        String refreshToken
) {
}


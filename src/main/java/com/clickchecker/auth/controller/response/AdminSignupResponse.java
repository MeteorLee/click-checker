package com.clickchecker.auth.controller.response;

public record AdminSignupResponse(
        Long accountId,
        String accessToken,
        long accessTokenExpiresIn,
        String refreshToken,
        long refreshTokenExpiresIn
) {
}

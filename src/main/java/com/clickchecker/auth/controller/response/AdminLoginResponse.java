package com.clickchecker.auth.controller.response;

public record AdminLoginResponse(
        Long accountId,
        String accessToken,
        long accessTokenExpiresIn,
        String refreshToken,
        long refreshTokenExpiresIn
) {
}

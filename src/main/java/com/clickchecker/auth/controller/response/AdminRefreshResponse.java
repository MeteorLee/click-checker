package com.clickchecker.auth.controller.response;

public record AdminRefreshResponse(
        Long accountId,
        String accessToken,
        long accessTokenExpiresIn,
        String refreshToken,
        long refreshTokenExpiresIn
) {
}

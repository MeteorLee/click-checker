package com.clickchecker.auth.service.result;

public record AdminTokenResult(
        Long accountId,
        String accessToken,
        long accessTokenExpiresIn,
        String refreshToken,
        long refreshTokenExpiresIn
) {
}

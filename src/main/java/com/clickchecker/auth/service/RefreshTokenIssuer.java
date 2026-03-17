package com.clickchecker.auth.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.HexFormat;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class RefreshTokenIssuer {

    private static final HexFormat HEX = HexFormat.of();

    private final SecureRandom secureRandom = new SecureRandom();
    private final long refreshTokenExpirationSeconds;

    public RefreshTokenIssuer(
            @Value("${app.jwt.refresh-token-expiration-seconds}") long refreshTokenExpirationSeconds
    ) {
        this.refreshTokenExpirationSeconds = refreshTokenExpirationSeconds;
    }

    public IssuedRefreshToken issue() {
        Instant now = Instant.now();
        String plainToken = "rt_" + randomHex(32);
        return new IssuedRefreshToken(
                plainToken,
                hash(plainToken),
                now.plusSeconds(refreshTokenExpirationSeconds)
        );
    }

    public String hash(String plainToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HEX.formatHex(digest.digest(plainToken.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("Failed to hash refresh token", ex);
        }
    }

    public long getRefreshTokenExpirationSeconds() {
        return refreshTokenExpirationSeconds;
    }

    private String randomHex(int byteSize) {
        byte[] bytes = new byte[byteSize];
        secureRandom.nextBytes(bytes);
        return HEX.formatHex(bytes);
    }

    public record IssuedRefreshToken(
            String plainToken,
            String tokenHash,
            Instant expiresAt
    ) {
    }
}

package com.clickchecker.organization.service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Locale;

public class ApiKeyIssuer {

    private static final HexFormat HEX = HexFormat.of();
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final SecureRandom secureRandom = new SecureRandom();
    private final String keyEnv;
    private final String pepper;

    public ApiKeyIssuer(String keyEnv, String pepper) {
        this.keyEnv = normalizeEnv(keyEnv);
        this.pepper = requirePepper(pepper);
    }

    public IssuedApiKey issue() {
        String kid = randomHex(10);
        String secret = randomHex(32);
        String plainKey = "ck_" + keyEnv + "_v1_" + kid + "_" + secret;
        String prefix = kid.substring(0, Math.min(8, kid.length()));
        String hash = hash(plainKey);
        return new IssuedApiKey(plainKey, kid, prefix, hash);
    }

    public String hash(String plainKey) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(pepper.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            byte[] digest = mac.doFinal(plainKey.getBytes(StandardCharsets.UTF_8));
            return HEX.formatHex(digest);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Failed to hash API key", e);
        }
    }

    public String extractKid(String plainKey) {
        if (plainKey == null || plainKey.isBlank()) {
            throw new IllegalArgumentException("API key is blank.");
        }

        String[] parts = plainKey.split("_", 5);
        if (parts.length < 5 || !"ck".equals(parts[0]) || !"v1".equals(parts[2])) {
            throw new IllegalArgumentException("Invalid API key format.");
        }
        return parts[3];
    }

    public boolean constantTimeEquals(String left, String right) {
        byte[] leftBytes = left == null ? new byte[0] : left.getBytes(StandardCharsets.UTF_8);
        byte[] rightBytes = right == null ? new byte[0] : right.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(leftBytes, rightBytes);
    }

    private String randomHex(int byteSize) {
        byte[] bytes = new byte[byteSize];
        secureRandom.nextBytes(bytes);
        return HEX.formatHex(bytes);
    }

    private String normalizeEnv(String keyEnv) {
        if (keyEnv == null || keyEnv.isBlank()) {
            return "live";
        }
        String normalized = keyEnv.toLowerCase(Locale.ROOT);
        if (!"live".equals(normalized) && !"test".equals(normalized)) {
            throw new IllegalArgumentException("Unsupported API key env: " + keyEnv);
        }
        return normalized;
    }

    private String requirePepper(String pepper) {
        if (pepper == null || pepper.isBlank()) {
            throw new IllegalStateException("API_KEY_PEPPER must not be blank.");
        }
        return pepper;
    }

    public record IssuedApiKey(
            String plainKey,
            String kid,
            String prefix,
            String hash
    ) {
    }
}

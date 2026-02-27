package com.clickchecker.logging;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public final class LogMaskingUtil {

    private LogMaskingUtil() {
    }

    public static String maskToken(String value) {
        if (isBlank(value)) {
            return value;
        }
        return "****";
    }

    public static String maskIdentifier(String value) {
        if (isBlank(value)) {
            return value;
        }
        if (value.length() <= 4) {
            return "****";
        }
        String head = value.substring(0, 2);
        String tail = value.substring(value.length() - 2);
        return head + "***" + tail;
    }

    public static String sha256(String value) {
        if (isBlank(value)) {
            return value;
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return toHex(hashed);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}

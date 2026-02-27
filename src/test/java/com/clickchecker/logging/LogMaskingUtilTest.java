package com.clickchecker.logging;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LogMaskingUtilTest {

    @Test
    void maskTokenShouldReturnMaskedValueForNonBlankInput() {
        assertThat(LogMaskingUtil.maskToken("secret-token")).isEqualTo("****");
    }

    @Test
    void maskTokenShouldKeepBlankValues() {
        assertThat(LogMaskingUtil.maskToken(null)).isNull();
        assertThat(LogMaskingUtil.maskToken("")).isEmpty();
        assertThat(LogMaskingUtil.maskToken("   ")).isBlank();
    }

    @Test
    void maskIdentifierShouldMaskMiddlePart() {
        assertThat(LogMaskingUtil.maskIdentifier("abcdef1234")).isEqualTo("ab***34");
    }

    @Test
    void maskIdentifierShouldFullyMaskShortValue() {
        assertThat(LogMaskingUtil.maskIdentifier("abcd")).isEqualTo("****");
        assertThat(LogMaskingUtil.maskIdentifier("abc")).isEqualTo("****");
    }

    @Test
    void sha256ShouldReturnDeterministicHex() {
        String hash = LogMaskingUtil.sha256("user-1001");

        assertThat(hash)
                .hasSize(64)
                .matches("[0-9a-f]{64}")
                .isEqualTo("bf579efa420261f06d69ed0649597b2bc1788d31d032e0d7dd0b21ace0864150");
    }

    @Test
    void sha256ShouldKeepBlankValues() {
        assertThat(LogMaskingUtil.sha256(null)).isNull();
        assertThat(LogMaskingUtil.sha256(" ")).isBlank();
    }
}

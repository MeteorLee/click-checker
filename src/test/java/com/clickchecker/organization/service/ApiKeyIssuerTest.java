package com.clickchecker.organization.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApiKeyIssuerTest {

    @Test
    void issueShouldGenerateDifferentKeys() {
        ApiKeyIssuer issuer = new ApiKeyIssuer("test", "pepper-123");

        ApiKeyIssuer.IssuedApiKey first = issuer.issue();
        ApiKeyIssuer.IssuedApiKey second = issuer.issue();

        assertThat(first.plainKey()).isNotEqualTo(second.plainKey());
        assertThat(first.plainKey()).startsWith("ck_test_v1_");
        assertThat(second.plainKey()).startsWith("ck_test_v1_");
        assertThat(first.prefix()).isEqualTo(first.kid().substring(0, 8));
        assertThat(first.kid()).isEqualTo(issuer.extractKid(first.plainKey()));
    }

    @Test
    void hashShouldReturn64Hex() {
        ApiKeyIssuer issuer = new ApiKeyIssuer("live", "pepper-abc");

        String hash = issuer.hash("ck_live_v1_kid_secret");

        assertThat(hash)
                .hasSize(64)
                .matches("[0-9a-f]{64}");
    }

    @Test
    void constructorShouldFailFastWhenPepperIsBlank() {
        assertThatThrownBy(() -> new ApiKeyIssuer("live", ""))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("API_KEY_PEPPER");
    }

    @Test
    void constantTimeEqualsShouldCompareSafely() {
        ApiKeyIssuer issuer = new ApiKeyIssuer("live", "pepper-xyz");
        assertThat(issuer.constantTimeEquals("abc", "abc")).isTrue();
        assertThat(issuer.constantTimeEquals("abc", "abd")).isFalse();
        assertThat(issuer.constantTimeEquals(null, "abd")).isFalse();
    }
}

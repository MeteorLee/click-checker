package com.clickchecker.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class JwtTokenProviderTest {

    private final JwtTokenProvider jwtTokenProvider =
            new JwtTokenProvider("local-dev-jwt-secret-local-dev-jwt-secret-123456", 900);

    @Test
    void shouldIssueAndParseAccessToken() {
        String token = jwtTokenProvider.issueAccessToken(1L);

        assertThat(jwtTokenProvider.isValidAccessToken(token)).isTrue();
        assertThat(jwtTokenProvider.extractAccountId(token)).isEqualTo(1L);
    }

    @Test
    void shouldRejectInvalidToken() {
        assertThat(jwtTokenProvider.isValidAccessToken("invalid-token")).isFalse();
        assertThatThrownBy(() -> jwtTokenProvider.extractAccountId("invalid-token"))
                .isInstanceOf(Exception.class);
    }
}

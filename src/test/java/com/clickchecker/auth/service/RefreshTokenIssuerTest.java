package com.clickchecker.auth.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class RefreshTokenIssuerTest {

    private final RefreshTokenIssuer refreshTokenIssuer = new RefreshTokenIssuer(1209600);

    @Test
    void issueShouldGenerateRefreshTokenAndHash() {
        RefreshTokenIssuer.IssuedRefreshToken first = refreshTokenIssuer.issue();
        RefreshTokenIssuer.IssuedRefreshToken second = refreshTokenIssuer.issue();

        assertThat(first.plainToken()).startsWith("rt_");
        assertThat(first.plainToken()).isNotEqualTo(second.plainToken());
        assertThat(first.tokenHash()).hasSize(64).matches("[0-9a-f]{64}");
        assertThat(first.tokenHash()).isEqualTo(refreshTokenIssuer.hash(first.plainToken()));
        assertThat(first.expiresAt()).isAfter(Instant.now());
    }
}

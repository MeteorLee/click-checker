package com.clickchecker.organization.service.result;

import java.time.Instant;

public record AdminOrganizationApiKeyRotateResult(
        String apiKey,
        String apiKeyPrefix,
        Instant rotatedAt
) {
}

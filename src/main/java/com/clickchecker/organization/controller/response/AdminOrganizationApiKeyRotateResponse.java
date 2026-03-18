package com.clickchecker.organization.controller.response;

import java.time.Instant;

public record AdminOrganizationApiKeyRotateResponse(
        String apiKey,
        String apiKeyPrefix,
        Instant rotatedAt
) {
}

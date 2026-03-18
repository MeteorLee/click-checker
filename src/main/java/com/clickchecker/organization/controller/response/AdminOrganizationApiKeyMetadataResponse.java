package com.clickchecker.organization.controller.response;

import java.time.Instant;

public record AdminOrganizationApiKeyMetadataResponse(
        String kid,
        String apiKeyPrefix,
        String status,
        Instant createdAt,
        Instant rotatedAt,
        Instant lastUsedAt
) {
}

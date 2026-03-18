package com.clickchecker.organization.service.result;

import java.time.Instant;

public record AdminOrganizationApiKeyMetadataResult(
        String kid,
        String apiKeyPrefix,
        String status,
        Instant createdAt,
        Instant rotatedAt,
        Instant lastUsedAt
) {
}

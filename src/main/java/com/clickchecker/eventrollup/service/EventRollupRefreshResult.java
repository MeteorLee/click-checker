package com.clickchecker.eventrollup.service;

import java.time.Instant;

public record EventRollupRefreshResult(
        Long organizationId,
        int batchCount,
        Instant processedCreatedAt
) {
}

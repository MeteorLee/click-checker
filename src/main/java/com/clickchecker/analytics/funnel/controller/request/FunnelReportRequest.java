package com.clickchecker.analytics.funnel.controller.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.Valid;

import java.time.Instant;
import java.util.List;

public record FunnelReportRequest(
        String externalUserId,
        @NotNull Instant from,
        @NotNull Instant to,
        @Positive
        @Max(365)
        Integer conversionWindowDays,
        @NotEmpty
        @Size(min = 2, max = 4)
        List<@Valid @NotNull FunnelStepRequest> steps
) {
}

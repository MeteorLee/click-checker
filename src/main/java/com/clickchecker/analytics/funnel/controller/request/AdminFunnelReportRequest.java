package com.clickchecker.analytics.funnel.controller.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

public record AdminFunnelReportRequest(
        @NotNull LocalDate from,
        @NotNull LocalDate to,
        @Positive
        @Max(365)
        Integer conversionWindowDays,
        @NotEmpty
        @Size(min = 2, max = 4)
        List<@Valid @NotNull FunnelStepRequest> steps
) {
}

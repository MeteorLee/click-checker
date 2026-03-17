package com.clickchecker.eventtype.controller.request;

import jakarta.validation.constraints.NotNull;

public record EventTypeMappingActiveUpdateRequest(
        @NotNull
        Boolean active
) {
}

package com.clickchecker.eventtype.controller.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record EventTypeMappingUpdateRequest(
        @NotBlank
        @Size(max = 100)
        String rawEventType,

        @NotBlank
        @Size(max = 100)
        String canonicalEventType
) {
}

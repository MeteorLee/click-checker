package com.clickchecker.event.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public record EventCreateRequest(
        String externalUserId,

        @NotBlank
        String eventType,

        @NotBlank
        String path,

        @NotNull
        Instant occurredAt,

        String payload
) {}

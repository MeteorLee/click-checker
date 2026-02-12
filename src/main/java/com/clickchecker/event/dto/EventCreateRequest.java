package com.clickchecker.event.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

public record EventCreateRequest(
        String eventType,

        @NotBlank
        String path,

        @NotNull
        LocalDateTime occurredAt,

        String payload
) {}
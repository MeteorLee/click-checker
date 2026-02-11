package com.clickchecker.event.dto;

import java.time.LocalDateTime;

public record EventCreateRequest(
        String eventType,
        LocalDateTime occurredAt,
        String payload
) {}
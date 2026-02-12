package com.clickchecker.event.dto;

import java.time.LocalDateTime;

public record EventCreateRequest(
        String eventType,
        String path,
        LocalDateTime occurredAt,
        String payload
) {}
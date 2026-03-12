package com.clickchecker.web.dto;

import com.clickchecker.web.tracking.TrafficState;

public record DrainStatusResponseDto(
        Boolean changed,
        TrafficState trafficState,
        int activeRequests
) {
}

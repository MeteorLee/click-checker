package com.clickchecker.analytics.funnel.controller.response;

import java.util.List;

public record AdminFunnelOptionsResponse(
        List<String> canonicalEventTypes,
        List<String> routeKeys
) {
}

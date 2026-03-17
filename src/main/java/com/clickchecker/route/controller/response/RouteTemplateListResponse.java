package com.clickchecker.route.controller.response;

import java.util.List;

public record RouteTemplateListResponse(
        List<RouteTemplateItem> items
) {
}

package com.clickchecker.route.controller.response;

public record RouteTemplateItem(
        Long id,
        String template,
        String routeKey,
        int priority,
        boolean active
) {
}

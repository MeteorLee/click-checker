package com.clickchecker.route.service;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RouteKeyResolver {

    public static final String UNMATCHED_ROUTE = "UNMATCHED_ROUTE";

    private final RouteTemplateCacheService routeTemplateCacheService;
    private final RoutePathMatcher routePathMatcher;

    public String resolve(Long organizationId, String rawPath) {
        List<RouteTemplateCacheService.CachedRouteTemplate> templates =
                routeTemplateCacheService.getActiveTemplates(organizationId);
        return resolve(rawPath, templates);
    }

    public Map<String, String> resolveAll(Long organizationId, Collection<String> rawPaths) {
        List<RouteTemplateCacheService.CachedRouteTemplate> templates =
                routeTemplateCacheService.getActiveTemplates(organizationId);

        Map<String, String> resolved = new LinkedHashMap<>();
        rawPaths.stream()
                .distinct()
                .forEach(rawPath -> resolved.put(rawPath, resolve(rawPath, templates)));
        return resolved;
    }

    private String resolve(String rawPath, List<RouteTemplateCacheService.CachedRouteTemplate> templates) {
        if (rawPath == null || rawPath.isBlank()) {
            return UNMATCHED_ROUTE;
        }

        for (RouteTemplateCacheService.CachedRouteTemplate template : templates) {
            if (routePathMatcher.matches(template.template(), rawPath)) {
                return template.routeKey();
            }
        }

        return UNMATCHED_ROUTE;
    }
}

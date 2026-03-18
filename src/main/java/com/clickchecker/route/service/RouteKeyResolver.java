package com.clickchecker.route.service;

import com.clickchecker.route.entity.RouteTemplate;
import com.clickchecker.route.repository.RouteTemplateRepository;
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

    private final RouteTemplateRepository routeTemplateRepository;
    private final RoutePathMatcher routePathMatcher;

    public String resolve(Long organizationId, String rawPath) {
        List<RouteTemplate> templates =
                routeTemplateRepository.findByOrganizationIdAndActiveTrueOrderByPriorityDescIdAsc(organizationId);
        return resolve(rawPath, templates);
    }

    public Map<String, String> resolveAll(Long organizationId, Collection<String> rawPaths) {
        List<RouteTemplate> templates =
                routeTemplateRepository.findByOrganizationIdAndActiveTrueOrderByPriorityDescIdAsc(organizationId);

        Map<String, String> resolved = new LinkedHashMap<>();
        rawPaths.stream()
                .distinct()
                .forEach(rawPath -> resolved.put(rawPath, resolve(rawPath, templates)));
        return resolved;
    }

    private String resolve(String rawPath, List<RouteTemplate> templates) {
        if (rawPath == null || rawPath.isBlank()) {
            return UNMATCHED_ROUTE;
        }

        for (RouteTemplate template : templates) {
            if (routePathMatcher.matches(template.getTemplate(), rawPath)) {
                return template.getRouteKey();
            }
        }

        return UNMATCHED_ROUTE;
    }
}

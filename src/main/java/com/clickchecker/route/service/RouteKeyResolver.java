package com.clickchecker.route.service;

import com.clickchecker.route.entity.RouteTemplate;
import com.clickchecker.route.repository.RouteTemplateRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RouteKeyResolver {

    public static final String UNMATCHED_ROUTE = "UNMATCHED_ROUTE";

    private final RouteTemplateRepository routeTemplateRepository;
    private final RoutePathMatcher routePathMatcher;

    public String resolve(Long organizationId, String rawPath) {
        if (rawPath == null || rawPath.isBlank()) {
            return UNMATCHED_ROUTE;
        }

        List<RouteTemplate> templates =
                routeTemplateRepository.findByOrganizationIdAndActiveTrueOrderByPriorityDescIdAsc(organizationId);

        for (RouteTemplate template : templates) {
            if (routePathMatcher.matches(template.getTemplate(), rawPath)) {
                return template.getRouteKey();
            }
        }

        return UNMATCHED_ROUTE;
    }
}

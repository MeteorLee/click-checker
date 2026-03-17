package com.clickchecker.route.service;

import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class RoutePathMatcher {

    public boolean matches(String template, String path) {
        List<String> templateSegments = segments(template);
        List<String> pathSegments = segments(path);

        if (templateSegments.size() != pathSegments.size()) {
            return false;
        }

        for (int i = 0; i < templateSegments.size(); i++) {
            String templateSegment = templateSegments.get(i);
            String pathSegment = pathSegments.get(i);

            if (isPathVariable(templateSegment)) {
                continue;
            }

            if (!templateSegment.equals(pathSegment)) {
                return false;
            }
        }

        return true;
    }

    private List<String> segments(String value) {
        return normalize(value).lines()
                .findFirst()
                .stream()
                .flatMap(v -> List.of(v.split("/")).stream())
                .filter(segment -> !segment.isBlank())
                .toList();
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.strip()
                .replaceAll("\\?.*$", "")
                .replaceAll("/{2,}", "/")
                .replaceAll("/$", "");
    }

    private boolean isPathVariable(String segment) {
        return segment.startsWith("{") && segment.endsWith("}") && segment.length() > 2;
    }
}

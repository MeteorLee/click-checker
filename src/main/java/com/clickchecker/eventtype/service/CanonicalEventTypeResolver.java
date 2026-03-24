package com.clickchecker.eventtype.service;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CanonicalEventTypeResolver {

    public static final String UNMAPPED_EVENT_TYPE = "UNMAPPED_EVENT_TYPE";

    private final EventTypeMappingCacheService eventTypeMappingCacheService;

    public String resolve(Long organizationId, String rawEventType) {
        List<EventTypeMappingCacheService.CachedEventTypeMapping> mappings =
                eventTypeMappingCacheService.getActiveMappings(organizationId);
        return resolve(rawEventType, mappings);
    }

    public Map<String, String> resolveAll(Long organizationId, Collection<String> rawEventTypes) {
        List<EventTypeMappingCacheService.CachedEventTypeMapping> mappings =
                eventTypeMappingCacheService.getActiveMappings(organizationId);

        Map<String, String> resolved = new LinkedHashMap<>();
        rawEventTypes.stream()
                .distinct()
                .forEach(rawEventType -> resolved.put(rawEventType, resolve(rawEventType, mappings)));
        return resolved;
    }

    private String resolve(String rawEventType, List<EventTypeMappingCacheService.CachedEventTypeMapping> mappings) {
        if (rawEventType == null || rawEventType.isBlank()) {
            return UNMAPPED_EVENT_TYPE;
        }

        String candidate = rawEventType.trim();

        for (EventTypeMappingCacheService.CachedEventTypeMapping mapping : mappings) {
            if (mapping.rawEventType().equals(candidate)) {
                return mapping.canonicalEventType();
            }
        }

        return UNMAPPED_EVENT_TYPE;
    }
}

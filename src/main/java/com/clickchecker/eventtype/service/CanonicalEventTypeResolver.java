package com.clickchecker.eventtype.service;

import com.clickchecker.eventtype.entity.EventTypeMapping;
import com.clickchecker.eventtype.repository.EventTypeMappingRepository;
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

    private final EventTypeMappingRepository eventTypeMappingRepository;

    public String resolve(Long organizationId, String rawEventType) {
        List<EventTypeMapping> mappings =
                eventTypeMappingRepository.findByOrganizationIdAndActiveTrueOrderByRawEventTypeAsc(organizationId);
        return resolve(rawEventType, mappings);
    }

    public Map<String, String> resolveAll(Long organizationId, Collection<String> rawEventTypes) {
        List<EventTypeMapping> mappings =
                eventTypeMappingRepository.findByOrganizationIdAndActiveTrueOrderByRawEventTypeAsc(organizationId);

        Map<String, String> resolved = new LinkedHashMap<>();
        rawEventTypes.stream()
                .distinct()
                .forEach(rawEventType -> resolved.put(rawEventType, resolve(rawEventType, mappings)));
        return resolved;
    }

    private String resolve(String rawEventType, List<EventTypeMapping> mappings) {
        if (rawEventType == null || rawEventType.isBlank()) {
            return UNMAPPED_EVENT_TYPE;
        }

        String candidate = rawEventType.trim();

        for (EventTypeMapping mapping : mappings) {
            if (mapping.getRawEventType().equals(candidate)) {
                return mapping.getCanonicalEventType();
            }
        }

        return UNMAPPED_EVENT_TYPE;
    }
}

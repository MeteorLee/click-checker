package com.clickchecker.eventtype.service;

import com.clickchecker.eventtype.entity.EventTypeMapping;
import com.clickchecker.eventtype.repository.EventTypeMappingRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CanonicalEventTypeResolver {

    public static final String UNMAPPED_EVENT_TYPE = "UNMAPPED_EVENT_TYPE";

    private final EventTypeMappingRepository eventTypeMappingRepository;

    public String resolve(Long organizationId, String rawEventType) {
        if (rawEventType == null || rawEventType.isBlank()) {
            return UNMAPPED_EVENT_TYPE;
        }

        String candidate = rawEventType.trim();
        List<EventTypeMapping> mappings =
                eventTypeMappingRepository.findByOrganizationIdAndActiveTrueOrderByRawEventTypeAsc(organizationId);

        for (EventTypeMapping mapping : mappings) {
            if (mapping.getRawEventType().equals(candidate)) {
                return mapping.getCanonicalEventType();
            }
        }

        return UNMAPPED_EVENT_TYPE;
    }
}

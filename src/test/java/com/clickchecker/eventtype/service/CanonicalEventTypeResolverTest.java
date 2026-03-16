package com.clickchecker.eventtype.service;

import com.clickchecker.eventtype.entity.EventTypeMapping;
import com.clickchecker.eventtype.repository.EventTypeMappingRepository;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CanonicalEventTypeResolverTest {

    private final EventTypeMappingRepository repository = mock(EventTypeMappingRepository.class);
    private final CanonicalEventTypeResolver resolver = new CanonicalEventTypeResolver(repository);

    @Test
    void resolve_returnsCanonicalEventType_whenExactRawEventTypeMatches() {
        when(repository.findByOrganizationIdAndActiveTrueOrderByRawEventTypeAsc(1L))
                .thenReturn(List.of(
                        EventTypeMapping.builder()
                                .rawEventType("button_click")
                                .canonicalEventType("click")
                                .active(true)
                                .build()
                ));

        String canonicalEventType = resolver.resolve(1L, "button_click");

        assertThat(canonicalEventType).isEqualTo("click");
    }

    @Test
    void resolve_returnsUnmappedEventType_whenNoMappingMatches() {
        when(repository.findByOrganizationIdAndActiveTrueOrderByRawEventTypeAsc(1L))
                .thenReturn(List.of(
                        EventTypeMapping.builder()
                                .rawEventType("button_click")
                                .canonicalEventType("click")
                                .active(true)
                                .build()
                ));

        String canonicalEventType = resolver.resolve(1L, "page_view");

        assertThat(canonicalEventType).isEqualTo(CanonicalEventTypeResolver.UNMAPPED_EVENT_TYPE);
    }

    @Test
    void resolve_returnsUnmappedEventType_whenRawEventTypeIsBlank() {
        assertThat(resolver.resolve(1L, null))
                .isEqualTo(CanonicalEventTypeResolver.UNMAPPED_EVENT_TYPE);
        assertThat(resolver.resolve(1L, " "))
                .isEqualTo(CanonicalEventTypeResolver.UNMAPPED_EVENT_TYPE);
    }
}

package com.clickchecker.eventtype.service;

import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CanonicalEventTypeResolverTest {

    private final EventTypeMappingCacheService eventTypeMappingCacheService = mock(EventTypeMappingCacheService.class);
    private final CanonicalEventTypeResolver resolver = new CanonicalEventTypeResolver(eventTypeMappingCacheService);

    @Test
    void resolve_returnsCanonicalEventType_whenExactRawEventTypeMatches() {
        when(eventTypeMappingCacheService.getActiveMappings(1L))
                .thenReturn(List.of(
                        new EventTypeMappingCacheService.CachedEventTypeMapping("button_click", "click")
                ));

        String canonicalEventType = resolver.resolve(1L, "button_click");

        assertThat(canonicalEventType).isEqualTo("click");
    }

    @Test
    void resolve_returnsUnmappedEventType_whenNoMappingMatches() {
        when(eventTypeMappingCacheService.getActiveMappings(1L))
                .thenReturn(List.of(
                        new EventTypeMappingCacheService.CachedEventTypeMapping("button_click", "click")
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

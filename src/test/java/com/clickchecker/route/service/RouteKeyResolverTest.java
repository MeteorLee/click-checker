package com.clickchecker.route.service;

import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RouteKeyResolverTest {

    private final RouteTemplateCacheService routeTemplateCacheService = mock(RouteTemplateCacheService.class);
    private final RoutePathMatcher matcher = new RoutePathMatcher();
    private final RouteKeyResolver resolver = new RouteKeyResolver(routeTemplateCacheService, matcher);

    @Test
    void resolve_returnsFirstMatchingRouteKeyInRepositoryOrder() {
        when(routeTemplateCacheService.getActiveTemplates(1L))
                .thenReturn(List.of(
                        new RouteTemplateCacheService.CachedRouteTemplate(
                                "/orders/{orderId}/items/{itemId}",
                                "/orders/{orderId}/items/{itemId}"
                        ),
                        new RouteTemplateCacheService.CachedRouteTemplate(
                                "/orders/{orderId}",
                                "/orders/{orderId}"
                        )
                ));

        String routeKey = resolver.resolve(1L, "/orders/10/items/3");

        assertThat(routeKey).isEqualTo("/orders/{orderId}/items/{itemId}");
    }

    @Test
    void resolve_returnsUnmatchedRoute_whenNoTemplateMatches() {
        when(routeTemplateCacheService.getActiveTemplates(1L))
                .thenReturn(List.of(
                        new RouteTemplateCacheService.CachedRouteTemplate(
                                "/posts/{id}",
                                "/posts/{id}"
                        )
                ));

        String routeKey = resolver.resolve(1L, "/comments/1");

        assertThat(routeKey).isEqualTo(RouteKeyResolver.UNMATCHED_ROUTE);
    }

    @Test
    void resolve_returnsUnmatchedRoute_whenRawPathIsBlank() {
        assertThat(resolver.resolve(1L, null)).isEqualTo(RouteKeyResolver.UNMATCHED_ROUTE);
        assertThat(resolver.resolve(1L, " ")).isEqualTo(RouteKeyResolver.UNMATCHED_ROUTE);
    }
}

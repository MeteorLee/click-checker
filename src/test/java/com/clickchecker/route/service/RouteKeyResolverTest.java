package com.clickchecker.route.service;

import com.clickchecker.route.entity.RouteTemplate;
import com.clickchecker.route.repository.RouteTemplateRepository;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RouteKeyResolverTest {

    private final RouteTemplateRepository repository = mock(RouteTemplateRepository.class);
    private final RoutePathMatcher matcher = new RoutePathMatcher();
    private final RouteKeyResolver resolver = new RouteKeyResolver(repository, matcher);

    @Test
    void resolve_returnsFirstMatchingRouteKeyInRepositoryOrder() {
        when(repository.findByOrganizationIdAndActiveTrueOrderByPriorityDescIdAsc(1L))
                .thenReturn(List.of(
                        RouteTemplate.builder()
                                .template("/orders/{orderId}/items/{itemId}")
                                .routeKey("/orders/{orderId}/items/{itemId}")
                                .priority(200)
                                .active(true)
                                .build(),
                        RouteTemplate.builder()
                                .template("/orders/{orderId}")
                                .routeKey("/orders/{orderId}")
                                .priority(100)
                                .active(true)
                                .build()
                ));

        String routeKey = resolver.resolve(1L, "/orders/10/items/3");

        assertThat(routeKey).isEqualTo("/orders/{orderId}/items/{itemId}");
    }

    @Test
    void resolve_returnsUnmatchedRoute_whenNoTemplateMatches() {
        when(repository.findByOrganizationIdAndActiveTrueOrderByPriorityDescIdAsc(1L))
                .thenReturn(List.of(
                        RouteTemplate.builder()
                                .template("/posts/{id}")
                                .routeKey("/posts/{id}")
                                .priority(100)
                                .active(true)
                                .build()
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

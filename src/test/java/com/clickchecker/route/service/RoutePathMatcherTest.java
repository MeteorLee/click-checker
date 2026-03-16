package com.clickchecker.route.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RoutePathMatcherTest {

    private final RoutePathMatcher matcher = new RoutePathMatcher();

    @Test
    void matches_returnsTrue_whenPathVariableSegmentsMatch() {
        assertThat(matcher.matches("/posts/{id}", "/posts/123")).isTrue();
        assertThat(matcher.matches("/orders/{orderId}/items/{itemId}", "/orders/10/items/3")).isTrue();
    }

    @Test
    void matches_returnsFalse_whenStaticSegmentsDoNotMatch() {
        assertThat(matcher.matches("/posts/{id}", "/articles/123")).isFalse();
        assertThat(matcher.matches("/orders/{orderId}", "/orders/10/items")).isFalse();
    }

    @Test
    void matches_normalizesDuplicateSlashTrailingSlashAndQueryString() {
        assertThat(matcher.matches("/posts/{id}", "//posts/123/")).isTrue();
        assertThat(matcher.matches("/posts/{id}", "/posts/123?sort=desc")).isTrue();
    }

    @Test
    void matches_returnsFalse_whenSegmentCountIsDifferent() {
        assertThat(matcher.matches("/posts/{id}", "/posts")).isFalse();
        assertThat(matcher.matches("/posts/{id}", "/posts/123/comments")).isFalse();
    }
}

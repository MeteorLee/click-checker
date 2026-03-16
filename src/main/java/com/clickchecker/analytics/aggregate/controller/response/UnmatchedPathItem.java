package com.clickchecker.analytics.aggregate.controller.response;

public record UnmatchedPathItem(
        String path,
        long count
) {
}

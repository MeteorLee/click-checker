package com.clickchecker.event.controller.response;

public record UnmatchedPathItem(
        String path,
        long count
) {
}

package com.clickchecker.event.repository.projection;

public record RawPathUserCountProjection(
        String path,
        Long eventUserId,
        Long count
) {
}

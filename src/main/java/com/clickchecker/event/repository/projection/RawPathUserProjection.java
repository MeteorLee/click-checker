package com.clickchecker.event.repository.projection;

public record RawPathUserProjection(
        String path,
        Long eventUserId
) {
}

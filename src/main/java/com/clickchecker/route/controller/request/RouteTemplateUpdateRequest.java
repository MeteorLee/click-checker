package com.clickchecker.route.controller.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RouteTemplateUpdateRequest(
        @NotBlank
        @Size(max = 512)
        String template,

        @NotBlank
        @Size(max = 512)
        String routeKey,

        @Min(0)
        Integer priority
) {
}

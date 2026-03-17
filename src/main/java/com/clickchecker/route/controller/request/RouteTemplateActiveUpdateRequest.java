package com.clickchecker.route.controller.request;

import jakarta.validation.constraints.NotNull;

public record RouteTemplateActiveUpdateRequest(
        @NotNull
        Boolean active
) {
}

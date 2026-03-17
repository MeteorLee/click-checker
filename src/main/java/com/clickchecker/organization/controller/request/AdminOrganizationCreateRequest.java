package com.clickchecker.organization.controller.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AdminOrganizationCreateRequest(
        @NotBlank
        @Size(max = 100)
        String name
) {
}

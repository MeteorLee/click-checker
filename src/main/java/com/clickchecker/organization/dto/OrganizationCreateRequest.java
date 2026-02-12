package com.clickchecker.organization.dto;

import jakarta.validation.constraints.NotBlank;

public record OrganizationCreateRequest(
        @NotBlank
        String name
) {
}

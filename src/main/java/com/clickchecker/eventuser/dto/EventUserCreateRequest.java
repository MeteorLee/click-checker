package com.clickchecker.eventuser.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record EventUserCreateRequest(
        @NotNull
        Long organizationId,

        @NotBlank
        String externalUserId
) {
}

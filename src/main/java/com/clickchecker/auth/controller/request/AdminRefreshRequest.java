package com.clickchecker.auth.controller.request;

import jakarta.validation.constraints.NotBlank;

public record AdminRefreshRequest(
        @NotBlank String refreshToken
) {
}

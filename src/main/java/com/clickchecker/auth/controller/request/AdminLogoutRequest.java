package com.clickchecker.auth.controller.request;

import jakarta.validation.constraints.NotBlank;

public record AdminLogoutRequest(
        @NotBlank String refreshToken
) {
}

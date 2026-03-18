package com.clickchecker.auth.controller.request;

import jakarta.validation.constraints.NotBlank;

public record AdminLoginRequest(
        @NotBlank String loginId,
        @NotBlank String password
) {
}

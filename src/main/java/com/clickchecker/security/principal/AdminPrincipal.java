package com.clickchecker.security.principal;

public record AdminPrincipal(
        Long accountId,
        String loginId
) {
}

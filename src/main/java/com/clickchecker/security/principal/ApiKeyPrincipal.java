package com.clickchecker.security.principal;

public record ApiKeyPrincipal(
        Long organizationId,
        String apiKeyKid
) {
}

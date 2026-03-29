package com.clickchecker.web.filter;

import com.clickchecker.logging.LogMaskingUtil;
import com.clickchecker.organization.entity.ApiKeyStatus;
import com.clickchecker.organization.entity.Organization;
import com.clickchecker.organization.repository.OrganizationRepository;
import com.clickchecker.organization.service.ApiKeyIssuer;
import com.clickchecker.security.principal.ApiKeyPrincipal;
import com.clickchecker.web.sentry.SentryRequestContextSupport;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

@RequiredArgsConstructor
public class ApiKeyAuthFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(ApiKeyAuthFilter.class);
    private static final Duration API_KEY_LAST_USED_REFRESH_INTERVAL = Duration.ofMinutes(1);

    public static final String API_KEY_HEADER = "X-API-Key";
    private final OrganizationRepository organizationRepository;
    private final ApiKeyIssuer apiKeyIssuer;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return "OPTIONS".equalsIgnoreCase(request.getMethod());
    }

    @Override
    protected boolean shouldNotFilterErrorDispatch() {
        return true;
    }

    @Override
    protected boolean shouldNotFilterAsyncDispatch() {
        return true;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String plainKey = request.getHeader(API_KEY_HEADER);
        if (plainKey == null || plainKey.isBlank()) {
            logAuthFailure("missing_api_key", request);
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        String kid;
        try {
            kid = apiKeyIssuer.extractKid(plainKey);
        } catch (IllegalArgumentException ex) {
            logAuthFailure("invalid_key_format", request);
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        Optional<Organization> candidate = organizationRepository.findByApiKeyKidAndApiKeyStatus(kid, ApiKeyStatus.ACTIVE);
        if (candidate.isEmpty()) {
            logAuthFailure("kid_not_found_or_inactive", request);
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        String hashed = apiKeyIssuer.hash(plainKey);
        Organization organization = candidate.get();
        if (!apiKeyIssuer.constantTimeEquals(hashed, organization.getApiKeyHash())) {
            logAuthFailure("hash_mismatch", request);
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        Instant now = Instant.now();
        if (shouldRefreshLastUsedAt(organization, now)) {
            organization.markApiKeyUsed(now);
            organizationRepository.save(organization);
        }
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(
                        new ApiKeyPrincipal(organization.getId(), kid),
                        null,
                        AuthorityUtils.NO_AUTHORITIES
                )
        );
        SentryRequestContextSupport.bindApiKeyAuthContext(organization.getId(), kid);
        log.debug(
                "api key auth success: method={}, path={}, orgId={}, kidMasked={}, requestId={}",
                request.getMethod(),
                request.getRequestURI(),
                organization.getId(),
                LogMaskingUtil.maskIdentifier(kid),
                LogMaskingUtil.maskIdentifier(MDC.get(RequestIdFilter.MDC_KEY))
        );
        filterChain.doFilter(request, response);
    }

    private void logAuthFailure(String reason, HttpServletRequest request) {
        log.warn(
                "api key auth failed: reason={}, method={}, path={}, requestId={}",
                reason,
                request.getMethod(),
                request.getRequestURI(),
                LogMaskingUtil.maskIdentifier(MDC.get(RequestIdFilter.MDC_KEY))
        );
    }

    private boolean shouldRefreshLastUsedAt(Organization organization, Instant now) {
        Instant lastUsedAt = organization.getApiKeyLastUsedAt();
        return lastUsedAt == null
                || lastUsedAt.isBefore(now.minus(API_KEY_LAST_USED_REFRESH_INTERVAL));
    }
}

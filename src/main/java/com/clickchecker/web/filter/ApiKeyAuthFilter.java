package com.clickchecker.web.filter;

import com.clickchecker.organization.entity.ApiKeyStatus;
import com.clickchecker.organization.entity.Organization;
import com.clickchecker.organization.repository.OrganizationRepository;
import com.clickchecker.organization.service.ApiKeyIssuer;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@RequiredArgsConstructor
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class ApiKeyAuthFilter extends OncePerRequestFilter {

    public static final String API_KEY_HEADER = "X-API-Key";
    public static final String AUTH_ORG_ID = "AUTH_ORG_ID";

    private final OrganizationRepository organizationRepository;
    private final ApiKeyIssuer apiKeyIssuer;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        boolean protectedPath = path != null && path.startsWith("/api/events");
        boolean optionsRequest = "OPTIONS".equalsIgnoreCase(request.getMethod());
        boolean errorPath = "/error".equals(path);
        return !protectedPath || optionsRequest || errorPath;
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
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        String kid;
        try {
            kid = apiKeyIssuer.extractKid(plainKey);
        } catch (IllegalArgumentException ex) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        Optional<Organization> candidate = organizationRepository.findByApiKeyKidAndApiKeyStatus(kid, ApiKeyStatus.ACTIVE);
        if (candidate.isEmpty()) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        String hashed = apiKeyIssuer.hash(plainKey);
        Organization organization = candidate.get();
        if (!apiKeyIssuer.constantTimeEquals(hashed, organization.getApiKeyHash())) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        request.setAttribute(AUTH_ORG_ID, organization.getId());
        filterChain.doFilter(request, response);
    }
}

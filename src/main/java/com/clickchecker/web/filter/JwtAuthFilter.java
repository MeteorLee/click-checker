package com.clickchecker.web.filter;

import com.clickchecker.account.entity.Account;
import com.clickchecker.account.repository.AccountRepository;
import com.clickchecker.auth.service.JwtTokenProvider;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@RequiredArgsConstructor
@Order(Ordered.HIGHEST_PRECEDENCE + 3)
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthFilter.class);
    private static final String BEARER_PREFIX = "Bearer ";

    public static final String AUTH_ACCOUNT_ID = "AUTH_ACCOUNT_ID";

    private final JwtTokenProvider jwtTokenProvider;
    private final AccountRepository accountRepository;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        boolean protectedPath = path != null && path.startsWith("/api/v1/admin");
        boolean authPath = path != null
                && (path.startsWith("/api/v1/admin/auth/login")
                || path.startsWith("/api/v1/admin/auth/refresh")
                || path.startsWith("/api/v1/admin/auth/logout"));
        boolean optionsRequest = "OPTIONS".equalsIgnoreCase(request.getMethod());
        boolean errorPath = "/error".equals(path);
        return !protectedPath || authPath || optionsRequest || errorPath;
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

        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
            logAuthFailure("missing_or_invalid_authorization_header", request);
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        String token = authorization.substring(BEARER_PREFIX.length()).trim();
        if (!jwtTokenProvider.isValidAccessToken(token)) {
            logAuthFailure("invalid_access_token", request);
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        Long accountId;
        try {
            accountId = jwtTokenProvider.extractAccountId(token);
        } catch (RuntimeException ex) {
            logAuthFailure("access_token_parse_failed", request);
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        Account account = accountRepository.findById(accountId).orElse(null);
        if (account == null) {
            logAuthFailure("account_not_found", request);
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        if (account.isDisabled()) {
            logAuthFailure("account_disabled", request);
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }

        request.setAttribute(AUTH_ACCOUNT_ID, account.getId());
        log.debug(
                "jwt auth success: method={}, path={}, accountId={}, requestId={}",
                request.getMethod(),
                request.getRequestURI(),
                account.getId(),
                MDC.get(RequestIdFilter.MDC_KEY)
        );
        filterChain.doFilter(request, response);
    }

    private void logAuthFailure(String reason, HttpServletRequest request) {
        log.warn(
                "jwt auth failed: reason={}, method={}, path={}, requestId={}",
                reason,
                request.getMethod(),
                request.getRequestURI(),
                MDC.get(RequestIdFilter.MDC_KEY)
        );
    }
}

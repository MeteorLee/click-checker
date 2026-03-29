package com.clickchecker.web.filter;

import com.clickchecker.account.entity.Account;
import com.clickchecker.account.repository.AccountRepository;
import com.clickchecker.auth.service.JwtTokenProvider;
import com.clickchecker.logging.LogMaskingUtil;
import com.clickchecker.security.principal.AdminPrincipal;
import com.clickchecker.web.sentry.SentryRequestContextSupport;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthFilter.class);
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenProvider jwtTokenProvider;
    private final AccountRepository accountRepository;

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

        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(
                        new AdminPrincipal(account.getId(), account.getLoginId()),
                        null,
                        AuthorityUtils.NO_AUTHORITIES
                )
        );
        SentryRequestContextSupport.bindJwtAuthContext(account.getId());
        log.debug(
                "jwt auth success: method={}, path={}, accountId={}, requestId={}",
                request.getMethod(),
                request.getRequestURI(),
                account.getId(),
                LogMaskingUtil.maskIdentifier(MDC.get(RequestIdFilter.MDC_KEY))
        );
        filterChain.doFilter(request, response);
    }

    private void logAuthFailure(String reason, HttpServletRequest request) {
        log.warn(
                "jwt auth failed: reason={}, method={}, path={}, requestId={}",
                reason,
                request.getMethod(),
                request.getRequestURI(),
                LogMaskingUtil.maskIdentifier(MDC.get(RequestIdFilter.MDC_KEY))
        );
    }
}

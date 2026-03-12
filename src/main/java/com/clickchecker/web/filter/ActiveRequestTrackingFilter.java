package com.clickchecker.web.filter;

import com.clickchecker.web.tracking.ActiveRequestTracker;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Slf4j
@Component
@RequiredArgsConstructor
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class ActiveRequestTrackingFilter extends OncePerRequestFilter {

    private final ActiveRequestTracker activeRequestTracker;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();

        return uri.startsWith("/actuator")
                || uri.startsWith("/internal/drain");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        int current = activeRequestTracker.increment();

        try {
            filterChain.doFilter(request, response);
        } finally {
            int remaining = activeRequestTracker.decrement();

            log.debug(
                    "request completed method={} uri={} status={} activeRequestsBeforeDecrement={} activeRequestsAfterDecrement={}",
                    request.getMethod(),
                    request.getRequestURI(),
                    response.getStatus(),
                    current,
                    remaining
            );
        }
    }
}

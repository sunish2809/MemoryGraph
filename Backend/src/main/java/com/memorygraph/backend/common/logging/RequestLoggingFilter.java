package com.memorygraph.backend.common.logging;

import java.io.IOException;
import java.util.UUID;

import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

/**
 * Assigns a request id (honouring an inbound one from a proxy), publishes it to the MDC and the
 * response headers, and emits one structured access log line per request.
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestLoggingFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        String requestId = resolveRequestId(request);
        MDC.put(RequestContext.REQUEST_ID_KEY, requestId);
        response.setHeader(RequestContext.REQUEST_ID_HEADER, requestId);

        long startedAt = System.nanoTime();
        try {
            chain.doFilter(request, response);
        } finally {
            long durationMs = (System.nanoTime() - startedAt) / 1_000_000;
            log.info("{} {} -> {} in {}ms", request.getMethod(), request.getRequestURI(), response.getStatus(),
                    durationMs);
            MDC.remove(RequestContext.REQUEST_ID_KEY);
            MDC.remove(RequestContext.USER_ID_KEY);
        }
    }

    private String resolveRequestId(HttpServletRequest request) {
        String inbound = request.getHeader(RequestContext.REQUEST_ID_HEADER);
        return StringUtils.hasText(inbound) ? inbound : UUID.randomUUID().toString();
    }
}

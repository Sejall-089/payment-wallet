package com.wallet.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Component
@Order(1)
public class MdcFilter extends OncePerRequestFilter {

    private static final String REQUEST_ID_HEADER = "X-Request-ID";
    private static final String MDC_REQUEST_ID    = "requestId";
    private static final String MDC_USER_ID       = "userId";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {
        try {
            // use client-provided request ID or generate one
            String requestId = request.getHeader(REQUEST_ID_HEADER);
            if (requestId == null || requestId.isBlank()) {
                requestId = UUID.randomUUID().toString().substring(0, 8);
            }

            MDC.put(MDC_REQUEST_ID, requestId);

            // also add requestId to response so client can correlate
            response.setHeader(REQUEST_ID_HEADER, requestId);

            filterChain.doFilter(request, response);

        } finally {
            // CRITICAL — always clear MDC after request
            // threads are reused from a pool
            // without this, next request on same thread gets previous request's MDC
            MDC.clear();
        }
    }
}
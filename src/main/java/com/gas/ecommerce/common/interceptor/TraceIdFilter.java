package com.gas.ecommerce.common.interceptor;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Security failures occur before MVC interceptors, so trace the full servlet chain. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter extends OncePerRequestFilter {
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String id = UUID.randomUUID().toString();
        request.setAttribute(TraceIdInterceptor.TRACE_ID, id);
        MDC.put(TraceIdInterceptor.TRACE_ID, id);
        response.setHeader("X-Request-Id", id);
        try { chain.doFilter(request, response); }
        finally { MDC.remove(TraceIdInterceptor.TRACE_ID); }
    }
}

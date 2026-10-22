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

/**
 * 拦截每个 HTTP 请求并为每个请求生成 traceId，并放入日志上下文和响应头。
 * 过滤器在 Spring Security 前执行，所以认证失败的请求也会带有 traceId。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter extends OncePerRequestFilter {
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        // UUID 几乎不会重复，可用于把一次请求的日志关联起来。
        String id = UUID.randomUUID().toString();
        request.setAttribute(TraceIdInterceptor.TRACE_ID, id);
        MDC.put(TraceIdInterceptor.TRACE_ID, id);
        response.setHeader("X-Request-Id", id);
        try { chain.doFilter(request, response); }
        // 线程可能会被服务器复用，必须清理 MDC，防止下个请求串到本次 traceId。
        finally { MDC.remove(TraceIdInterceptor.TRACE_ID); }
    }
}

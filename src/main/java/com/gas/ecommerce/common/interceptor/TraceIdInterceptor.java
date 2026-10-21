package com.gas.ecommerce.common.interceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class TraceIdInterceptor implements HandlerInterceptor {
    public static final String TRACE_ID = "traceId";
    public static String currentTraceId() { return MDC.get(TRACE_ID); }
    @Override public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        Object existing = request.getAttribute(TRACE_ID);
        String id = existing instanceof String trace ? trace : UUID.randomUUID().toString();
        MDC.put(TRACE_ID, id);
        response.setHeader("X-Request-Id", id);
        return true;
    }
    @Override public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) { MDC.remove(TRACE_ID); }
}

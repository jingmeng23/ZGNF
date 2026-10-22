package com.gas.ecommerce.config;

import com.gas.ecommerce.common.interceptor.TraceIdInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
/**
 * 应用配置类，负责向 Spring 注册和配置基础设施组件。
 */
public class WebMvcConfig implements WebMvcConfigurer {
    private final TraceIdInterceptor traceIdInterceptor;
    public WebMvcConfig(TraceIdInterceptor traceIdInterceptor) { this.traceIdInterceptor = traceIdInterceptor; }
    @Override public void addInterceptors(InterceptorRegistry registry) { registry.addInterceptor(traceIdInterceptor); }
}

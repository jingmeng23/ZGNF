package com.gas.ecommerce.config;

import com.gas.ecommerce.common.interceptor.TraceIdInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.beans.factory.annotation.Value;

@Configuration
/**
 * 应用配置类，负责向 Spring 注册和配置基础设施组件。
 */
public class WebMvcConfig implements WebMvcConfigurer {
    private final TraceIdInterceptor traceIdInterceptor;
    @Value("${app.product-images.directory:./uploads/products}")
    private String productImageDirectory;
    public WebMvcConfig(TraceIdInterceptor traceIdInterceptor) { this.traceIdInterceptor = traceIdInterceptor; }
    @Override public void addInterceptors(InterceptorRegistry registry) { registry.addInterceptor(traceIdInterceptor); }
    @Override public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String location = java.nio.file.Path.of(productImageDirectory).toAbsolutePath().normalize().toUri().toString();
        registry.addResourceHandler("/api/v1/media/products/**")
                .addResourceLocations(location.endsWith("/") ? location : location + "/");
    }
}

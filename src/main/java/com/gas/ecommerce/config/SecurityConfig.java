package com.gas.ecommerce.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gas.ecommerce.common.exception.ErrorCode;
import com.gas.ecommerce.common.result.Result;
import com.gas.ecommerce.common.security.JwtAuthenticationFilter;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * 配置 HTTP 请求的认证和授权规则。
 * JWT 过滤器从请求头识别用户；未明确放行的接口都要求有效的 access token。
 */
@Configuration
public class SecurityConfig {
    /**
     * 创建 Spring Security 的过滤器链。
     * 应用不保存服务端登录 Session，因此客户端需要在每次请求中携带令牌。
     */
    @Bean SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAuthenticationFilter jwtFilter, ObjectMapper mapper) throws Exception {
        // 当前使用 Authorization Bearer Token；若改用 Cookie 登录，应重新评估 CSRF 防护。
        return http.csrf(csrf -> csrf.disable()).cors(Customizer.withDefaults())
                // 无状态模式不会用服务器 Session 记住登录用户。
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/v1/admin/auth/login").permitAll()
                        .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/v1/users/register", "/api/v1/users/login/password").permitAll()
                        .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/v1/payment/wechat/notify").permitAll()
                        .requestMatchers("/api/v1/users/me/wechat-binding").hasRole("USER")
                        .requestMatchers("/api/v1/mall/me/**").hasRole("USER")
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/v1/mall/**").permitAll()
                        .requestMatchers(org.springframework.http.HttpMethod.GET,
                                "/api/v1/products", "/api/v1/products/*", "/api/v1/categories", "/api/v1/categories/tree").permitAll()
                        // 登录、支付平台回调、API 文档和健康检查无需先登录。
                        .requestMatchers("/api/v1/auth/**", "/api/v1/payment/callback/**", "/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**", "/actuator/health").permitAll()
                        // hasRole("ADMIN") 实际检查的权限名是 ROLE_ADMIN。
                        .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                         .requestMatchers("/api/v1/coupons", "/api/v1/coupons/**", "/api/v1/user-coupons", "/api/v1/user-coupons/**").hasRole("USER")
                         .requestMatchers("/api/v1/work-orders", "/api/v1/work-orders/**").hasRole("USER")
                        .requestMatchers("/api/v1/users/me/addresses", "/api/v1/users/me/addresses/**").hasRole("USER")
                        .requestMatchers("/api/v1/cart", "/api/v1/cart/**").hasRole("USER")
                        .requestMatchers("/api/v1/payments", "/api/v1/payments/**").hasRole("USER")
                        .requestMatchers("/api/v1/orders", "/api/v1/orders/**").hasRole("USER")
                        .requestMatchers("/api/v1/aftersales", "/api/v1/aftersales/**").hasRole("USER")
                        // 其余请求必须先通过 JWT 认证。
                        .anyRequest().authenticated())
                .exceptionHandling(ex -> ex.authenticationEntryPoint((request, response, cause) -> {
                    // 尚未登录或令牌无效时，返回 HTTP 401 和统一 JSON 响应。
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
                    mapper.writeValue(response.getOutputStream(), Result.of(ErrorCode.UNAUTHENTICATED, "请先登录", null));
                }).accessDeniedHandler((request, response, cause) -> {
                    // 已登录但权限不足时，返回 HTTP 403 和统一 JSON 响应。
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
                    mapper.writeValue(response.getOutputStream(), Result.of(ErrorCode.ACCESS_DENIED, "无权访问", null));
                }))
                // 在 Spring 默认用户名密码过滤器之前解析 JWT。
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class).build();
    }
    /** BCrypt 使用随机盐做单向哈希，适合保存密码摘要，不能用来还原明文密码。 */
    @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }
}

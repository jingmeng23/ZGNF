package com.gas.ecommerce.common.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
/**
 * 安全组件，负责 JWT 校验、登录身份建立或当前用户读取。
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    /** 令牌工具负责验签并提取用户 ID 和角色。 */
    private final JwtTokenProvider tokens;
    public JwtAuthenticationFilter(JwtTokenProvider tokens) { this.tokens = tokens; }
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        // Bearer 是 Authorization 请求头中表示令牌认证方式的标准前缀。
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            // Refresh token 虽然有效，也不能用来访问普通 API。
            if (tokens.validateAccessToken(token)) {
                Long userId = tokens.getUserIdFromToken(token);
                String role = tokens.getRoleFromToken(token);
                var authorities = role == null ? List.<SimpleGrantedAuthority>of() : List.of(new SimpleGrantedAuthority("ROLE_" + role.toUpperCase()));
                // Spring Security 后续授权规则从 SecurityContext 读取当前用户和角色。
                // 这里使用 Long 作为 principal，因此 SecurityUtils 可以直接取到 userId。
                SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(userId, null, authorities));
            }
        }
        // 无论请求是否带令牌，都继续后续过滤器；受保护路径会在授权阶段拒绝未认证请求。
        chain.doFilter(request, response);
    }
}

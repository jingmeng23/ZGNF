package com.gas.ecommerce.common.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
/**
 * 从 SecurityContext 读取当前请求的身份信息。
 * 本类不执行任何校验，只做读取与类型转换；真正的认证逻辑在 JwtAuthenticationFilter。
 * 注意：principal 的类型约定为 AuthPrincipal（或 Long），见 currentUserId()。
 */

public final class SecurityUtils {
    private SecurityUtils() {}
    public static Long currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getPrincipal() instanceof Long id ? id : null;
    }
}

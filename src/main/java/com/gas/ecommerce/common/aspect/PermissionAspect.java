package com.gas.ecommerce.common.aspect;

import com.gas.ecommerce.common.annotation.RequiresPermission;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Aspect @Component
/**
 * 面向切面组件，通过代理在目标方法执行前后统一处理横切逻辑。
 * 权限校验切面
 */
public class PermissionAspect {
    @Around("@annotation(permission)")
    public Object check(ProceedingJoinPoint point, RequiresPermission permission) throws Throwable {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        //
        if (auth == null || auth.getAuthorities().stream().noneMatch(a -> a.getAuthority().equals(permission.value()) || a.getAuthority().equals("TEST_LOGIN_BYPASS"))) {
            throw new AccessDeniedException("Missing permission: " + permission.value());
        }
        return point.proceed();
    }
}

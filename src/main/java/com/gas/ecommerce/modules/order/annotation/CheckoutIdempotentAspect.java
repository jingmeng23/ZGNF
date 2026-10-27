package com.gas.ecommerce.modules.order.annotation;

import com.gas.ecommerce.common.exception.BusinessException;
import com.gas.ecommerce.modules.order.dto.OrderCreateRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.*;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.*;

@Aspect
@Component
public class CheckoutIdempotentAspect {
    @Around("@annotation(com.gas.ecommerce.modules.order.annotation.CheckoutIdempotent)")
    public Object guard(ProceedingJoinPoint invocation) throws Throwable {
        OrderCreateRequest request = null;
        for (Object argument : invocation.getArgs()) if (argument instanceof OrderCreateRequest value) request = value;
        if (request == null || !request.isIdempotencyKeyPresent()) throw new BusinessException("缺少结算幂等键");
        if (request.orderNo() != null && (request.checkoutToken() == null || request.checkoutToken().isBlank()))
            throw new BusinessException("必须提供结算预览返回的 checkoutToken");
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes context) {
            String header = context.getRequest().getHeader("Idempotency-Key");
            String expected = request.orderNo() == null ? request.requestId() : request.checkoutToken();
            if (header != null && !header.equals(expected)) throw new BusinessException("Idempotency-Key 与结算凭证不一致");
        }
        // Never use the generic Redis SETNX guard: it rejects successful network retries instead of replaying.
        return invocation.proceed();
    }
}

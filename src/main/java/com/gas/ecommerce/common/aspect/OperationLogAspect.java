package com.gas.ecommerce.common.aspect;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Aspect @Component
/**
 * 面向切面组件，通过代理在目标方法执行前后统一处理横切逻辑。
 * 操作日志切面
 */
public class OperationLogAspect {
    private static final Logger log = LoggerFactory.getLogger(OperationLogAspect.class);
    @Around("within(com.gas.ecommerce.modules..controller..*)")
    public Object logOperation(ProceedingJoinPoint point) throws Throwable {
        long started = System.currentTimeMillis();
        try { return point.proceed(); }
        finally { log.info("operation={} durationMs={}", point.getSignature().toShortString(), System.currentTimeMillis() - started); }
    }
}

package com.gas.ecommerce.common.aspect;

import com.gas.ecommerce.common.annotation.Idempotent;
import com.gas.ecommerce.common.exception.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Aspect
@Component
public class IdempotentAspect {
    private final StringRedisTemplate redis;
    public IdempotentAspect(StringRedisTemplate redis) { this.redis = redis; }
    @Around("@annotation(idempotent)")
    public Object guard(ProceedingJoinPoint point, Idempotent idempotent) throws Throwable {
        String key = null;
        var attrs = RequestContextHolder.getRequestAttributes();
        if (attrs instanceof ServletRequestAttributes servletAttrs) {
            HttpServletRequest request = servletAttrs.getRequest();
            key = request.getHeader("Idempotency-Key");
            if (key == null || key.isBlank()) key = request.getParameter("idempotencyKey");
        }
        if (key == null || key.isBlank() || key.length() > 128) throw new BusinessException("请提供不超过128字符的 Idempotency-Key");
        String scope = com.gas.ecommerce.common.security.SecurityUtils.currentUserId() + ":" + point.getSignature().toLongString() + ":" + key;
        String redisKey = "idem:" + java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                .digest(scope.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        String owner = UUID.randomUUID().toString();
        Boolean fresh = redis.opsForValue().setIfAbsent(redisKey, owner, java.time.Duration.ofSeconds(idempotent.ttlSeconds()));
        if (!Boolean.TRUE.equals(fresh)) throw new BusinessException("请勿重复提交");
        try { return point.proceed(); }
        catch (Throwable ex) {
            var release = new org.springframework.data.redis.core.script.DefaultRedisScript<Long>("""
                    if redis.call('GET', KEYS[1]) == ARGV[1] then return redis.call('DEL', KEYS[1]) end
                    return 0
                    """, Long.class);
            try { redis.execute(release, java.util.List.of(redisKey), owner); }
            catch (RuntimeException cleanup) { ex.addSuppressed(cleanup); }
            throw ex;
        }
    }
}

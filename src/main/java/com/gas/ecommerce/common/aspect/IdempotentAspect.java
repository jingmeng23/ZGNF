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
/**
 * 面向切面组件，通过代理在目标方法执行前后统一处理横切逻辑。
 * 幂等性切面，防止重复提交，作用在下单请求等。
 */
public class IdempotentAspect {
    /** Redis 用于原子地占用幂等键，防止相同请求同时执行多次。 */
    private final StringRedisTemplate redis;
    public IdempotentAspect(StringRedisTemplate redis) { this.redis = redis; }
    @Around("@annotation(idempotent)")
    //ProceedingJoinPoint point,只有 Around 通知才有这个参数，它代表“被拦截的那次调用本身”。point.proceed() 就是真的去执行目标方法。你不叫它，方法就永远不会执行（拦截就是这么实现的）
    public Object guard(ProceedingJoinPoint point, Idempotent idempotent) throws Throwable {
        // 优先读取标准请求头；也兼容表单/查询参数 idempotencyKey。
        String key = null;
        var attrs = RequestContextHolder.getRequestAttributes();
        if (attrs instanceof ServletRequestAttributes servletAttrs) {
            HttpServletRequest request = servletAttrs.getRequest();
            key = request.getHeader("Idempotency-Key");
            if (key == null || key.isBlank()) key = request.getParameter("idempotencyKey");
        }
        // 没有请求上下文时（例如非 HTTP 调用）无法提供幂等键，明确拒绝执行。
        // Legacy callers may omit the header; new clients send it for retry safety.
        if (key == null || key.isBlank()) key = UUID.randomUUID().toString();
        if (key.length() > 128) throw new BusinessException("请提供不超过128字符的 Idempotency-Key");
        // 将用户、目标方法和客户端键组合成作用域，避免不同用户/接口误共享同一把锁。
        String scope = com.gas.ecommerce.common.security.SecurityUtils.currentUserId() + ":" + point.getSignature().toLongString() + ":" + key;
        // Redis 键使用摘要，避免直接暴露客户端传来的键值，也避免键过长。
        String redisKey = "idem:" + java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                .digest(scope.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        String owner = UUID.randomUUID().toString();
        // SET NX + TTL 是原子操作：只有第一个请求能获得执行权。
        Boolean fresh;
        try {
            fresh = redis.opsForValue().setIfAbsent(redisKey, owner, java.time.Duration.ofSeconds(idempotent.ttlSeconds()));
        } catch (RuntimeException unavailable) {
            // Redis is an optimization for retries; preserve cart availability when it is down.
            return point.proceed();
        }
        if (!Boolean.TRUE.equals(fresh)) throw new BusinessException("请勿重复提交");
        try { return point.proceed(); }
        catch (Throwable ex) {
            // 业务失败时释放本请求占用的键，允许客户端修正后重试。
            // Lua 同时检查 owner，避免误删其他请求后来写入的键。
            var release = new org.springframework.data.redis.core.script.DefaultRedisScript<Long>("""
                    if redis.call('GET', KEYS[1]) == ARGV[1] then return redis.call('DEL', KEYS[1]) end
                    return 0
                    """, Long.class);
            try { redis.execute(release, java.util.List.of(redisKey), owner); }
            catch (RuntimeException cleanup) { ex.addSuppressed(cleanup); }
            throw ex;
        }
        // 成功时不删除键，让它保留到 TTL 到期；重复提交会被拒绝，但不会重放原响应结果。
    }
}

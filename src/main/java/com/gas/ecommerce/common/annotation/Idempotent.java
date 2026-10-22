package com.gas.ecommerce.common.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
/**
 * 标记需要 Redis 幂等检查的方法。切面会要求请求提供 Idempotency-Key，默认保留 30 分钟。
 * 这只阻止重复执行，不会缓存并重放第一次请求的响应。
 */
public @interface Idempotent {
    /** Redis 中幂等键的存活秒数，默认 1800 秒（30 分钟）。 */
    long ttlSeconds() default 1800;
}

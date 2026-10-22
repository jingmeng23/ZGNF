package com.gas.ecommerce.common.utils;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
/**
 * 通用工具类，封装可重复使用的辅助功能。
 */
public class PaymentNoGenerator {
    private final StringRedisTemplate redis;
    public PaymentNoGenerator(StringRedisTemplate redis) { this.redis = redis; }
    public String next() {
        String day = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        Long sequence = redis.opsForValue().increment("seq:payment:" + day);
        return "PAY" + day + String.format("%08d", sequence == null ? 0 : sequence);
    }
}

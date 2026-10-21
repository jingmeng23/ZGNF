package com.gas.ecommerce.common.utils;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class OrderNoGenerator {
    private final StringRedisTemplate redis;
    public OrderNoGenerator(StringRedisTemplate redis) { this.redis = redis; }
    public String next() {
        String day = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        Long sequence = redis.opsForValue().increment("seq:order:" + day);
        return "GAS" + day + String.format("%08d", sequence == null ? 0 : sequence);
    }
}

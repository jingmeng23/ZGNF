package com.gas.ecommerce.modules.mall.service;

import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.TOO_MANY_REQUESTS;

@Service
public class MallRequestLimiter {
    private static final int LIMIT = 120;
    private static final DefaultRedisScript<Long> WINDOW_SCRIPT = new DefaultRedisScript<>(
            "local n=redis.call('incr',KEYS[1]); if n==1 then redis.call('pexpire',KEYS[1],ARGV[1]) end; return n", Long.class);
    private final StringRedisTemplate redis;

    public MallRequestLimiter(StringRedisTemplate redis) { this.redis = redis; }

    public void check(String clientIp) {
        String key = "mall:rate:" + clientIp;
        try {
            Long count = redis.execute(WINDOW_SCRIPT, java.util.List.of(key), "60000");
            if (count != null && count > LIMIT) throw new ResponseStatusException(TOO_MANY_REQUESTS, "Request limit exceeded");
        } catch (ResponseStatusException limited) {
            throw limited;
        } catch (Exception unavailable) {
            LoggerFactory.getLogger(getClass()).warn("Mall rate limiter unavailable; request allowed: {}", unavailable.toString());
        }
    }
}

package com.gas.ecommerce.modules.auth.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.gas.ecommerce.common.exception.BusinessException;
import com.gas.ecommerce.common.security.JwtTokenProvider;
import com.gas.ecommerce.common.utils.EncryptUtils;
import com.gas.ecommerce.modules.auth.dto.LoginRequest;
import com.gas.ecommerce.modules.auth.dto.LoginResponse;
import com.gas.ecommerce.modules.auth.service.AuthService;
import com.gas.ecommerce.modules.user.entity.User;
import com.gas.ecommerce.modules.user.mapper.UserMapper;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

@Service
public class AuthServiceImpl implements AuthService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final DefaultRedisScript<Long> CONSUME_CODE = new DefaultRedisScript<>("""
            if redis.call('GET', KEYS[1]) == ARGV[1] then
                return redis.call('DEL', KEYS[1])
            end
            return 0
            """, Long.class);
    private final StringRedisTemplate redis;
    private final JwtTokenProvider tokens;
    private final UserMapper users;
    private final RabbitTemplate rabbit;
    private final String encryptionKey;
    private final long expiresIn;

    public AuthServiceImpl(StringRedisTemplate redis, JwtTokenProvider tokens, UserMapper users, RabbitTemplate rabbit,
                           @Value("${app.encryption-key}") String encryptionKey,
                           @Value("${app.jwt.access-expiration-ms:7200000}") long expiration) {
        this.redis = redis; this.tokens = tokens; this.users = users; this.rabbit = rabbit;
        this.encryptionKey = encryptionKey; this.expiresIn = expiration / 1000;
    }

    @Override public void sendSmsCode(String phone) {
        String hash = EncryptUtils.fingerprint(phone, encryptionKey);
        if (!Boolean.TRUE.equals(redis.opsForValue().setIfAbsent("sms:rate:" + hash, "1", Duration.ofMinutes(1)))) {
            throw new BusinessException("验证码发送频繁，请稍后再试");
        }
        String code = "%06d".formatted(RANDOM.nextInt(1_000_000));
        redis.opsForValue().set("sms:code:" + hash, code, Duration.ofMinutes(5));
        // A provider consumer decrypts the phone using the same encryption key and sends the SMS.
        // Publishing to this queue acknowledges submission, not delivery by a provider.
        try {
            rabbit.convertAndSend("", "gas.notification.sms", Map.of(
                    "phone", EncryptUtils.encryptAes(phone, encryptionKey), "code", code));
        } catch (RuntimeException ex) {
            redis.delete(List.of("sms:code:" + hash, "sms:rate:" + hash));
            throw ex;
        }
    }

    @Override public LoginResponse login(LoginRequest request) {
        String hash = EncryptUtils.fingerprint(request.phone(), encryptionKey);
        Long consumed = redis.execute(CONSUME_CODE, List.of("sms:code:" + hash), request.code());
        if (!Long.valueOf(1).equals(consumed)) throw new BusinessException("验证码错误或已过期");
        User user = find(hash);
        if (user == null) {
            user = new User();
            user.setPhone(EncryptUtils.encryptAes(request.phone(), encryptionKey));
            user.setPhoneHash(hash); user.setPhoneLast4(request.phone().substring(7));
            user.setStatus("active"); user.setIsDeleted(0);
            try { users.insert(user); }
            catch (DuplicateKeyException ex) { user = find(hash); }
        }
        if (user == null || !"active".equals(user.getStatus())) throw new BusinessException("用户已停用");
        return new LoginResponse(tokens.generateAccessToken(user.getId(), "user"), tokens.generateRefreshToken(user.getId()), expiresIn);
    }
    private User find(String hash) { return users.selectOne(Wrappers.<User>lambdaQuery().eq(User::getPhoneHash, hash)); }
}

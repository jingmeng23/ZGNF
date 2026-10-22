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
/**
 * 认证业务的具体实现：发送验证码、校验验证码、创建用户并签发 JWT。
 * 短信目前只会投递到 RabbitMQ 队列；真正调用短信厂商的消费者还需要另行开发。
 */
public class AuthServiceImpl implements AuthService {
    /** 用安全随机数产生验证码，避免使用可预测的普通随机数。 */
    private static final SecureRandom RANDOM = new SecureRandom();
    /** Redis Lua 脚本将“比较验证码”和“删除验证码”放在一次原子操作中，避免并发重复使用。 */
    private static final DefaultRedisScript<Long> CONSUME_CODE = new DefaultRedisScript<>("""
            if redis.call('GET', KEYS[1]) == ARGV[1] then
                return redis.call('DEL', KEYS[1])
            end
            return 0
            """, Long.class);
    /** 读写 Redis 中的验证码和发送频率限制。 */
    private final StringRedisTemplate redis;
    /** 根据用户 ID 生成和校验 JWT。 */
    private final JwtTokenProvider tokens;
    /** 用户数据库访问接口。 */
    private final UserMapper users;
    /** 将短信任务发送到 RabbitMQ。 */
    private final RabbitTemplate rabbit;
    /** 用于加密手机号及生成不可逆手机号指纹的密钥。 */
    private final String encryptionKey;
    /** 登录响应中的 access token 有效秒数。 */
    private final long expiresIn;

    /** Spring 会按参数类型自动注入依赖；@Value 从配置文件读取密钥和有效期。 */
    public AuthServiceImpl(StringRedisTemplate redis, JwtTokenProvider tokens, UserMapper users, RabbitTemplate rabbit,
                           @Value("${app.encryption-key}") String encryptionKey,
                           @Value("${app.jwt.access-expiration-ms:7200000}") long expiration) {
        this.redis = redis; this.tokens = tokens; this.users = users; this.rabbit = rabbit;
        this.encryptionKey = encryptionKey; this.expiresIn = expiration / 1000;
    }

    /** 生成并提交短信验证码；phone 在进入本方法前已由 DTO 校验格式。 */
    @Override public void sendSmsCode(String phone) {
        // Redis 中只用手机号指纹做键，避免把手机号明文放进缓存键。
        String hash = EncryptUtils.fingerprint(phone, encryptionKey);
        // setIfAbsent 等价于 Redis SET NX：同一手机号一分钟内只能成功设置一次。
        if (!Boolean.TRUE.equals(redis.opsForValue().setIfAbsent("sms:rate:" + hash, "1", Duration.ofMinutes(1)))) {
            throw new BusinessException("验证码发送频繁，请稍后再试");
        }
        // 验证码补足为 6 位，并在 Redis 中最多保留 5 分钟。
        String code = "%06d".formatted(RANDOM.nextInt(1_000_000));
        redis.opsForValue().set("sms:code:" + hash, code, Duration.ofMinutes(5));
        // 队列消息中的手机号使用 AES 加密；短信消费者需使用同一密钥解密后调用短信服务商。
        // RabbitMQ 接收成功只代表任务已入队，不代表短信已经送达。
        try {
            rabbit.convertAndSend("", "gas.notification.sms", Map.of(
                    "phone", EncryptUtils.encryptAes(phone, encryptionKey), "code", code));
        } catch (RuntimeException ex) {
            redis.delete(List.of("sms:code:" + hash, "sms:rate:" + hash));
            throw ex;
        }
    }

    /** 校验一次性验证码；成功后查找或注册用户，并返回访问令牌和刷新令牌。 */
    @Override public LoginResponse login(LoginRequest request) {
        // 用确定性指纹找到该手机号对应的缓存验证码和用户记录。
        String hash = EncryptUtils.fingerprint(request.phone(), encryptionKey);
        // Lua 脚本匹配成功时立即删除验证码，保证同一验证码不能再次登录。
        Long consumed = redis.execute(CONSUME_CODE, List.of("sms:code:" + hash), request.code());
        if (!Long.valueOf(1).equals(consumed)) throw new BusinessException("验证码错误或已过期");
        User user = find(hash);
        if (user == null) {
            // 首次登录即注册：密文用于后续需要手机号的场景，指纹用于查重和查询。
            user = new User();
            user.setPhone(EncryptUtils.encryptAes(request.phone(), encryptionKey));
            user.setPhoneHash(hash); user.setPhoneLast4(request.phone().substring(7));
            user.setStatus("active"); user.setIsDeleted(0);
            try { users.insert(user); }
            // 两个首次登录请求可能同时插入；唯一索引让其中一个失败，随后读取已创建的用户。
            catch (DuplicateKeyException ex) { user = find(hash); }
        }
        // 停用账号不能继续获得令牌。
        if (user == null || !"active".equals(user.getStatus())) throw new BusinessException("用户已停用");
        // Access token 用于访问 API，refresh token 仅供后续刷新流程使用。
        return new LoginResponse(tokens.generateAccessToken(user.getId(), "user"), tokens.generateRefreshToken(user.getId()), expiresIn);
    }
    /** 按手机号指纹查找一条用户记录；lambdaQuery 可避免手写列名字符串。 */
    private User find(String hash) { return users.selectOne(Wrappers.<User>lambdaQuery().eq(User::getPhoneHash, hash)); }
}

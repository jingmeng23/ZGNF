package com.gas.ecommerce.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
/**
 *签发 token 和 验签解析
 */
public class JwtTokenProvider {
    /** HMAC 签名密钥；签名用于验证令牌未被篡改，JWT 内容本身并未加密。 */
    private final javax.crypto.SecretKey key;
    /** Access token 的有效期，单位毫秒。 */
    private final long accessMillis;
    /** Refresh token 的有效期，单位毫秒。 */
    private final long refreshMillis;
    /** 从配置读取密钥和有效期，并构造 HS256 使用的密钥对象。 */
    public JwtTokenProvider(@Value("${app.jwt.secret}") String secret,
                            @Value("${app.jwt.access-expiration-ms:7200000}") long accessMillis,
                            @Value("${app.jwt.refresh-expiration-ms:604800000}") long refreshMillis) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessMillis = accessMillis;
        this.refreshMillis = refreshMillis;
    }
    /** 生成用于访问受保护 API 的短期令牌，额外携带角色信息。 */
    public String generateAccessToken(Long userId, String role) { return generate(userId, role, accessMillis, "access"); }
    /** 生成较长期刷新令牌；过滤器不会接受它作为普通 API 的访问令牌。 */
    public String generateRefreshToken(Long userId) { return generate(userId, null, refreshMillis, "refresh"); }
    /** 公共生成步骤：写入用户主体、类型和时间，再用 HS256 签名。 */
    private String generate(Long userId, String role, long ttl, String type) {
        var builder = Jwts.builder().subject(userId.toString()).claim("type", type).issuedAt(new Date()).expiration(Date.from(Instant.now().plusMillis(ttl)));
        if (role != null) builder.claim("role", role);
        return builder.signWith(key, Jwts.SIG.HS256).compact();
    }
    /** 检查签名和过期时间；此方法不限制令牌是 access 还是 refresh。 */
    public boolean validateToken(String token) {
        try { parse(token); return true; } catch (RuntimeException ex) { return false; }
    }
    /** API 认证专用检查：除签名和有效期外，还必须是 access 类型。 */
    public boolean validateAccessToken(String token) {
        try { return "access".equals(parse(token).get("type", String.class)); } catch (RuntimeException ex) { return false; }
    }
    /** 从 JWT 的 subject（主体）读取用户 ID。调用前应先校验令牌。 */
    public Long getUserIdFromToken(String token) { return Long.valueOf(parse(token).getSubject()); }
    public String getRoleFromToken(String token) { return parse(token).get("role", String.class); }
    /** 解析并验证签名；无效、过期或格式错误时 JJWT 会抛出异常。 */
    private Claims parse(String token) { return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload(); }
}

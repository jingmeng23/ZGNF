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
public class JwtTokenProvider {
    private final javax.crypto.SecretKey key;
    private final long accessMillis;
    private final long refreshMillis;
    public JwtTokenProvider(@Value("${app.jwt.secret}") String secret,
                            @Value("${app.jwt.access-expiration-ms:7200000}") long accessMillis,
                            @Value("${app.jwt.refresh-expiration-ms:604800000}") long refreshMillis) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessMillis = accessMillis;
        this.refreshMillis = refreshMillis;
    }
    public String generateAccessToken(Long userId, String role) { return generate(userId, role, accessMillis, "access"); }
    public String generateRefreshToken(Long userId) { return generate(userId, null, refreshMillis, "refresh"); }
    private String generate(Long userId, String role, long ttl, String type) {
        var builder = Jwts.builder().subject(userId.toString()).claim("type", type).issuedAt(new Date()).expiration(Date.from(Instant.now().plusMillis(ttl)));
        if (role != null) builder.claim("role", role);
        return builder.signWith(key, Jwts.SIG.HS256).compact();
    }
    public boolean validateToken(String token) {
        try { parse(token); return true; } catch (RuntimeException ex) { return false; }
    }
    public boolean validateAccessToken(String token) {
        try { return "access".equals(parse(token).get("type", String.class)); } catch (RuntimeException ex) { return false; }
    }
    public Long getUserIdFromToken(String token) { return Long.valueOf(parse(token).getSubject()); }
    public String getRoleFromToken(String token) { return parse(token).get("role", String.class); }
    private Claims parse(String token) { return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload(); }
}

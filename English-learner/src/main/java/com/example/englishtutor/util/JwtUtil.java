package com.example.englishtutor.util;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

/**
 * JWT 工具类 — 双 Token 机制
 * <p>
 * Access Token：  短有效期（30min），携带用户信息，用于 API 鉴权
 * Refresh Token： 长有效期（10 年），落库 sys_refresh_token，用于无感刷新 Access Token
 * <p>
 * 设计思路：
 * - Access Token 过期 → 前端拿 Refresh Token 换新 Access（用户无感）
 * - Refresh Token 落库：重启/发版不清空登录态，退出登录时删行即可吊销，也可主动删除实现"踢人登录"
 * - 每个 Token 带 jti（唯一ID），可用于黑名单机制
 */
@Component
public class JwtUtil {

    @Value("${jwt.secret:EnglishTutorSecretKey2024!@#VeryLongSecureKey}")
    private String secret;

    @Value("${jwt.access-expiration:1800000}")
    private long accessExpiration;      // 30 分钟

    @Value("${jwt.refresh-expiration:315360000000}")
    private long refreshExpiration;    // 10 年：配合落库 + 退出登录删行吊销，实现「登录一次，除非退出不用再登」

    private SecretKey getKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 生成 Access Token（短时效，携带用户信息）
     */
    public String generateAccess(Long userId, String username, String role) {
        return buildToken(userId, username, role, accessExpiration);
    }

    /**
     * 生成 Refresh Token（长时效，仅用于刷新）
     */
    public String generateRefresh(Long userId) {
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(userId.toString())
                .claim("type", "refresh")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + refreshExpiration))
                .signWith(getKey())
                .compact();
    }

    private String buildToken(Long userId, String username, String role, long expiration) {
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(userId.toString())
                .claim("username", username)
                .claim("role", role)
                .claim("type", "access")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(getKey())
                .compact();
    }

    /**
     * 解析 Token
     */
    public Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(getKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * 获取 Access Token 有效期（毫秒）
     */
    public long getAccessExpiration() {
        return accessExpiration;
    }

    /**
     * 获取 Refresh Token 有效期（毫秒）
     */
    public long getRefreshExpiration() {
        return refreshExpiration;
    }
}

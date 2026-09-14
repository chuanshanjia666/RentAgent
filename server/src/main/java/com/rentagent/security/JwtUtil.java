package com.rentagent.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

/** JWT 签发与校验（jjwt 0.12 API），claims 携带 uid 与 role */
@Component
public class JwtUtil {

    private final SecretKey key;
    private final long expireHours;

    public JwtUtil(@Value("${rentagent.jwt-secret}") String secret,
                   @Value("${rentagent.jwt-expire-hours}") long expireHours) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expireHours = expireHours;
    }

    public String issue(long userId, int role) {
        Instant now = Instant.now();
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(String.valueOf(userId))
                .claim("role", role)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(expireHours * 3600)))
                .signWith(key)
                .compact();
    }

    /** 校验失败抛 JjwtException；返回 claims（含 uid/role/jti） */
    public Claims verify(String token) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }
}

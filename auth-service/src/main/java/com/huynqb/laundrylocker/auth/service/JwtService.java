package com.huynqb.laundrylocker.auth.service;

import com.huynqb.laundrylocker.common.security.SecuritySecrets;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Date;
import java.util.Set;

@Service
public class JwtService {

    @Value("${app.security.jwt.secret:laundry-locker-microservices-secret-key-change-me-please-32chars}")
    private String secret;

    @Value("${app.security.jwt.expiration-ms:86400000}")
    private long accessExpirationMs;

    @Value("${app.security.jwt.refresh-expiration-ms:2592000000}")
    private long refreshExpirationMs;

    private final Environment environment;
    private SecretKey key;

    public JwtService(Environment environment) {
        this.environment = environment;
    }

    @PostConstruct
    void init() {
        key =
                SecuritySecrets.hmacShaKeyFor(
                        secret, "app.security.jwt.secret", environment.getActiveProfiles());
    }

    public TokenPair issue(Long accountId, Long userId, Set<String> roles) {
        Instant now = Instant.now();
        Instant accessExpiresAt = now.plusMillis(accessExpirationMs);
        Instant refreshExpiresAt = now.plusMillis(refreshExpirationMs);
        String accessToken = buildToken(accountId, userId, roles, "access", accessExpiresAt);
        String refreshToken = buildToken(accountId, userId, roles, "refresh", refreshExpiresAt);
        return new TokenPair(accessToken, refreshToken, accessExpiresAt, refreshExpiresAt);
    }

    public Claims parse(String token) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }

    private String buildToken(
            Long accountId, Long userId, Set<String> roles, String tokenUse, Instant expiresAt) {
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("accountId", accountId)
                .claim("roles", roles)
                .claim("tokenUse", tokenUse)
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(expiresAt))
                .signWith(key)
                .compact();
    }

    public record TokenPair(
            String accessToken, String refreshToken, Instant accessExpiresAt, Instant refreshExpiresAt) {
    }
}

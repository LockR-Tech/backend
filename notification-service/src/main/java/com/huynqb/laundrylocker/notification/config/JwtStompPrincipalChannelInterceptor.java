package com.huynqb.laundrylocker.notification.config;

import com.huynqb.laundrylocker.common.security.SecuritySecrets;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.security.Principal;
import java.util.Arrays;
import java.util.List;

@Component
public class JwtStompPrincipalChannelInterceptor implements ChannelInterceptor {

    private static final String JWT_SECRET_PROPERTY = "app.security.jwt.secret";

    private final SecretKey key;

    @Autowired
    public JwtStompPrincipalChannelInterceptor(
            Environment environment,
            @Value(
                    "${app.security.jwt.secret:"
                            + "laundry-locker-microservices-secret-key-change-me-please-32chars}")
            String secret) {
        this(secret, environment.getActiveProfiles());
    }

    JwtStompPrincipalChannelInterceptor(String secret, String... activeProfiles) {
        this.key = SecuritySecrets.hmacShaKeyFor(secret, JWT_SECRET_PROPERTY, activeProfiles);
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        // Header còn mutable (luồng thật của Spring) thì đặt user ngay trên accessor gốc: Spring
        // nhớ user đó cho cả phiên, nên các frame SUBSCRIBE sau mang đúng người dùng — cần cho việc
        // kiểm quyền topic. Bản bọc lại (wrap) chỉ đổi header của riêng frame CONNECT.
        StompHeaderAccessor mutable = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (mutable != null && mutable.isMutable()) {
            if (StompCommand.CONNECT.equals(mutable.getCommand())) {
                mutable.setUser(authenticate(mutable));
            }
            return message;
        }
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
        if (!StompCommand.CONNECT.equals(accessor.getCommand())) {
            return message;
        }

        accessor.setUser(authenticate(accessor));
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(message.getPayload(), accessor.getMessageHeaders());
    }

    Principal authenticateBearerHeader(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new MessagingException("Missing STOMP bearer token");
        }

        Claims claims = parseClaims(authHeader.substring(7));
        if (!"access".equals(claims.get("tokenUse", String.class))) {
            throw new MessagingException("STOMP bearer token must be an access token");
        }
        String subject = claims.getSubject();
        if (subject == null || subject.isBlank()) {
            throw new MessagingException("STOMP bearer token must include a subject");
        }
        return new StompPrincipal(subject, roles(claims));
    }

    /// Claim `roles` dạng mảng hoặc chuỗi "A,B" — cùng cách đọc với gateway.
    private static List<String> roles(Claims claims) {
        Object value = claims.get("roles");
        if (value instanceof List<?> list) {
            return list.stream().filter(String.class::isInstance).map(String.class::cast).toList();
        }
        if (value instanceof String roles && !roles.isBlank()) {
            return Arrays.stream(roles.split(",")).map(String::trim).filter(role -> !role.isBlank()).toList();
        }
        return List.of();
    }

    private Principal authenticate(StompHeaderAccessor accessor) {
        String authHeader = firstHeader(accessor, "Authorization");
        if (authHeader == null) {
            authHeader = firstHeader(accessor, "authorization");
        }
        return authenticateBearerHeader(authHeader);
    }

    private Claims parseClaims(String token) {
        try {
            return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
        } catch (JwtException | IllegalArgumentException ex) {
            throw new MessagingException("Invalid STOMP bearer token", ex);
        }
    }

    private String firstHeader(StompHeaderAccessor accessor, String name) {
        String value = accessor.getFirstNativeHeader(name);
        return value == null || value.isBlank() ? null : value.trim();
    }
}

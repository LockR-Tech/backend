package com.huynqb.laundrylocker.notification.config;

import com.huynqb.laundrylocker.common.security.SecuritySecrets;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;

import javax.crypto.SecretKey;
import java.security.Principal;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class JwtStompPrincipalChannelInterceptorTest {

    private static final String SECRET = "notification-test-secret-with-enough-entropy-2026";
    private static final String SUBJECT = "42";

    private final JwtStompPrincipalChannelInterceptor interceptor =
            new JwtStompPrincipalChannelInterceptor(SECRET, "test");
    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner()
                    .withUserConfiguration(
                            JwtStompPrincipalChannelInterceptor.class, JwtHandshakePrincipalHandler.class)
                    .withPropertyValues("app.security.jwt.secret=" + SECRET);

    @Test
    void springContextCreatesWebSocketAuthBeans() {
        contextRunner.run(
                context -> {
                    assertTrue(context.containsBean("jwtStompPrincipalChannelInterceptor"));
                    assertTrue(context.containsBean("jwtHandshakePrincipalHandler"));
                });
    }

    @Test
    void connectWithAccessTokenSetsUserPrincipal() {
        Message<?> result = interceptor.preSend(connectMessage("Authorization", accessToken()), null);

        assertPrincipalName(result, SUBJECT);
    }

    @Test
    void connectAcceptsLowercaseAuthorizationHeader() {
        Message<?> result = interceptor.preSend(connectMessage("authorization", accessToken()), null);

        assertPrincipalName(result, SUBJECT);
    }

    @Test
    void connectWithRefreshTokenIsRejected() {
        MessagingException ex =
                assertThrows(
                        MessagingException.class,
                        () -> interceptor.preSend(connectMessage("Authorization", token("refresh")), null));

        assertEquals("STOMP bearer token must be an access token", ex.getMessage());
    }

    @Test
    void connectWithoutBearerTokenIsRejected() {
        MessagingException ex =
                assertThrows(
                        MessagingException.class,
                        () -> interceptor.preSend(connectMessage("Authorization", null), null));

        assertEquals("Missing STOMP bearer token", ex.getMessage());
    }

    @Test
    void connectWithInvalidTokenIsRejected() {
        MessagingException ex =
                assertThrows(
                        MessagingException.class,
                        () -> interceptor.preSend(connectMessage("Authorization", "not-a-jwt"), null));

        assertEquals("Invalid STOMP bearer token", ex.getMessage());
    }

    @Test
    void nonConnectMessagePassesThrough() {
        Message<byte[]> subscribeMessage = stompMessage(StompCommand.SUBSCRIBE, "Authorization", null);

        assertSame(subscribeMessage, interceptor.preSend(subscribeMessage, null));
    }

    @Test
    void connectPrincipalCarriesRolesFromToken() {
        Message<?> result = interceptor.preSend(connectMessage("Authorization", tokenWithRoles(List.of("DRONE_TECHNICIAN"))), null);

        StompPrincipal principal = assertInstanceOf(StompPrincipal.class, StompHeaderAccessor.wrap(result).getUser());
        assertEquals(List.of("DRONE_TECHNICIAN"), principal.roles());
    }

    /// Luồng thật của Spring: header CONNECT còn mutable và Spring nhớ user qua callback cho cả phiên.
    @Test
    void connectOnMutableHeadersStoresUserForTheSession() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setNativeHeader("Authorization", "Bearer " + tokenWithRoles("ADMIN,DRONE_TECHNICIAN"));
        AtomicReference<Principal> sessionUser = new AtomicReference<>();
        accessor.setUserChangeCallback(sessionUser::set);
        accessor.setLeaveMutable(true);
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        Message<?> result = interceptor.preSend(message, null);

        assertSame(message, result);
        StompPrincipal principal = assertInstanceOf(StompPrincipal.class, sessionUser.get());
        assertEquals(SUBJECT, principal.getName());
        assertEquals(List.of("ADMIN", "DRONE_TECHNICIAN"), principal.roles());
    }

    private String tokenWithRoles(Object roles) {
        SecretKey key = SecuritySecrets.hmacShaKeyFor(SECRET, "app.security.jwt.secret", "test");
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(SUBJECT)
                .claim("tokenUse", "access")
                .claim("roles", roles)
                .issuedAt(Date.from(now.minusSeconds(10)))
                .expiration(Date.from(now.plusSeconds(3600)))
                .signWith(key)
                .compact();
    }

    private String accessToken() {
        return token("access");
    }

    private String token(String tokenUse) {
        SecretKey key = SecuritySecrets.hmacShaKeyFor(SECRET, "app.security.jwt.secret", "test");
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(SUBJECT)
                .claim("tokenUse", tokenUse)
                .issuedAt(Date.from(now.minusSeconds(10)))
                .expiration(Date.from(now.plusSeconds(3600)))
                .signWith(key)
                .compact();
    }

    private Message<byte[]> connectMessage(String headerName, String token) {
        return stompMessage(StompCommand.CONNECT, headerName, token);
    }

    private Message<byte[]> stompMessage(StompCommand command, String headerName, String token) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
        if (token != null) {
            accessor.setNativeHeader(headerName, "Bearer " + token);
        }
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private void assertPrincipalName(Message<?> message, String expectedName) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
        assertNotNull(accessor.getUser());
        assertEquals(expectedName, accessor.getUser().getName());
    }
}

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
import java.time.Instant;
import java.util.Date;

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

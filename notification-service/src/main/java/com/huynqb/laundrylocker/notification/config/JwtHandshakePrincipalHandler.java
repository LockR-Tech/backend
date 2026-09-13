package com.huynqb.laundrylocker.notification.config;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;

import java.security.Principal;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class JwtHandshakePrincipalHandler extends DefaultHandshakeHandler {

    private final JwtStompPrincipalChannelInterceptor jwtPrincipalInterceptor;

    @Override
    protected Principal determineUser(
            ServerHttpRequest request, WebSocketHandler wsHandler, Map<String, Object> attributes) {
        String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || authHeader.isBlank()) {
            return super.determineUser(request, wsHandler, attributes);
        }
        return jwtPrincipalInterceptor.authenticateBearerHeader(authHeader.trim());
    }
}

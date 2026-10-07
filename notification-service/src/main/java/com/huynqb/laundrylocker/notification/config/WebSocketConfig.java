package com.huynqb.laundrylocker.notification.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final JwtStompPrincipalChannelInterceptor jwtStompPrincipalChannelInterceptor;
    private final JwtHandshakePrincipalHandler jwtHandshakePrincipalHandler;

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic", "/queue");
        config.setApplicationDestinationPrefixes("/app");
        config.setUserDestinationPrefix("/user");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(jwtStompPrincipalChannelInterceptor);
    }

    /// Gateway đã gắn CORS cho mọi đường dẫn (globalcors). SockJS mà gắn thêm thì `/ws/info` có hai
    /// header `Access-Control-Allow-Origin` và trình duyệt chặn, nên web admin không có realtime.
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry
                .addEndpoint("/ws")
                .setHandshakeHandler(jwtHandshakePrincipalHandler)
                .setAllowedOriginPatterns("*")
                .withSockJS()
                .setSuppressCors(true);
        registry
                .addEndpoint("/ws")
                .setHandshakeHandler(jwtHandshakePrincipalHandler)
                .setAllowedOriginPatterns("*");
    }
}

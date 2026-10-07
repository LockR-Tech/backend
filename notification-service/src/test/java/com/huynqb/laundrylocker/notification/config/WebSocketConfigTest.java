package com.huynqb.laundrylocker.notification.config;

import org.junit.jupiter.api.Test;
import org.springframework.messaging.support.ExecutorSubscribableChannel;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.servlet.handler.SimpleUrlHandlerMapping;
import org.springframework.web.socket.config.annotation.WebMvcStompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;
import org.springframework.web.socket.messaging.SubProtocolWebSocketHandler;
import org.springframework.web.socket.sockjs.support.AbstractSockJsService;
import org.springframework.web.socket.sockjs.support.SockJsHttpRequestHandler;

import static org.junit.jupiter.api.Assertions.*;

class WebSocketConfigTest {

    /// Gateway đã gắn CORS; SockJS gắn thêm thì /ws/info có hai `Access-Control-Allow-Origin`
    /// và trình duyệt chặn kết nối realtime của web admin.
    @Test
    void sockJsEndpointLeavesCorsHeadersToGateway() {
        JwtStompPrincipalChannelInterceptor interceptor =
                new JwtStompPrincipalChannelInterceptor("notification-test-secret-with-enough-entropy-2026", "test");
        WebSocketConfig config = new WebSocketConfig(interceptor, new JwtHandshakePrincipalHandler(interceptor));
        WebMvcStompEndpointRegistry registry = new WebMvcStompEndpointRegistry(
                new SubProtocolWebSocketHandler(new ExecutorSubscribableChannel(), new ExecutorSubscribableChannel()),
                new WebSocketTransportRegistration(),
                new ThreadPoolTaskScheduler());

        config.registerStompEndpoints(registry);

        SimpleUrlHandlerMapping mapping = (SimpleUrlHandlerMapping) registry.getHandlerMapping();
        SockJsHttpRequestHandler sockJs = mapping.getUrlMap().values().stream()
                .filter(SockJsHttpRequestHandler.class::isInstance)
                .map(SockJsHttpRequestHandler.class::cast)
                .findFirst()
                .orElseThrow(() -> new AssertionError("SockJS endpoint /ws is not registered"));
        assertTrue(((AbstractSockJsService) sockJs.getSockJsService()).shouldSuppressCors());
    }
}

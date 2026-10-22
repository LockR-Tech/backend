package com.huynqb.laundrylocker.notification.config;

import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;

import java.security.Principal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BrokerDestinationGuardInterceptorTest {

    private final BrokerDestinationGuardInterceptor interceptor = new BrokerDestinationGuardInterceptor();

    /// Thông báo đơn (kể cả tin "Mã mở tủ" gửi người nhận) và vị trí KTV phát lên các topic chung này.
    @Test
    void customersCannotListenToBroadcastTopics() {
        for (String topic : List.of(
                "/topic/orders", "/topic/orders/12", "/topic/notifications",
                "/topic/technicians/presence", "/topic/technicians/9/presence", "/topic/lockers", "/topic/lockers/3")) {
            assertThrows(MessagingException.class, () -> interceptor.preSend(subscribe(topic, customer()), null), topic);
            assertThrows(MessagingException.class,
                    () -> interceptor.preSend(subscribe(topic, user("8", "LOCKER_TECHNICIAN")), null), topic);
        }
    }

    @Test
    void adminListensToBroadcastTopics() {
        Message<byte[]> message = subscribe("/topic/notifications", user("1", "ADMIN"));

        assertSame(message, interceptor.preSend(message, null));
    }

    @Test
    void sessionWithoutUserCannotListenToBroadcastTopics() {
        assertThrows(MessagingException.class, () -> interceptor.preSend(subscribe("/topic/orders", null), null));
    }

    @Test
    void deliveryTopicsAndOwnQueuesAreLeftToTheirChecks() {
        for (String destination : List.of(
                "/topic/deliveries/21/position", "/user/queue/notifications", "/user/queue/orders")) {
            Message<byte[]> message = subscribe(destination, customer());
            assertSame(message, interceptor.preSend(message, null), destination);
        }
    }

    /// Đăng ký thẳng `/queue/...` hay theo mẫu sẽ nhận hộp thư riêng của người khác.
    @Test
    void rawQueuesAndWildcardsAreRefused() {
        for (String destination : List.of(
                "/queue/notifications-user1", "/queue/**", "/**", "/t*/**", "/topic/deliveries/*/position")) {
            assertThrows(MessagingException.class,
                    () -> interceptor.preSend(subscribe(destination, customer()), null), destination);
        }
        assertThrows(MessagingException.class,
                () -> interceptor.preSend(subscribe("/queue/**", user("1", "ADMIN")), null));
    }

    @Test
    void clientsOnlySendToApplicationDestinations() {
        assertThrows(MessagingException.class, () -> interceptor.preSend(send("/topic/notifications"), null));
        assertThrows(MessagingException.class, () -> interceptor.preSend(send("/queue/orders-user1"), null));
        Message<byte[]> app = send("/app/ping");
        assertSame(app, interceptor.preSend(app, null));
    }

    private static StompPrincipal customer() {
        return user("44", "CUSTOMER");
    }

    private static StompPrincipal user(String id, String role) {
        return new StompPrincipal(id, List.of(role));
    }

    private static Message<byte[]> subscribe(String destination, Principal user) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination(destination);
        accessor.setSubscriptionId("sub-1");
        if (user != null) {
            accessor.setUser(user);
        }
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private static Message<byte[]> send(String destination) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SEND);
        accessor.setDestination(destination);
        accessor.setUser(user("1", "ADMIN"));
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }
}

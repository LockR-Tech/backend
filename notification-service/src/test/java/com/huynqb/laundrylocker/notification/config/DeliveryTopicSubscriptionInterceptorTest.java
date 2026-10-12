package com.huynqb.laundrylocker.notification.config;

import com.huynqb.laundrylocker.common.dto.ApiResponse;
import com.huynqb.laundrylocker.notification.client.OrderParticipantClient;
import com.huynqb.laundrylocker.notification.dto.OrderParticipants;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;

import java.security.Principal;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class DeliveryTopicSubscriptionInterceptorTest {

    private static final String POSITION = "/topic/deliveries/21/position";

    private final AtomicInteger lookups = new AtomicInteger();
    private final OrderParticipantClient client = orderId -> {
        lookups.incrementAndGet();
        return ApiResponse.ok(new OrderParticipants(orderId, 44L, 55L));
    };
    private final DeliveryTopicSubscriptionInterceptor interceptor = new DeliveryTopicSubscriptionInterceptor(client);

    @Test
    void orderOwnerMaySubscribeToPosition() {
        Message<byte[]> message = subscribe(POSITION, customer("44"));

        assertSame(message, interceptor.preSend(message, null));
    }

    @Test
    void receiverAccountMaySubscribeToPosition() {
        Message<byte[]> message = subscribe(POSITION, customer("55"));

        assertSame(message, interceptor.preSend(message, null));
    }

    @Test
    void otherCustomerIsRefused() {
        assertThrows(MessagingException.class, () -> interceptor.preSend(subscribe(POSITION, customer("77")), null));
    }

    @Test
    void sessionWithoutUserIsRefused() {
        assertThrows(MessagingException.class, () -> interceptor.preSend(subscribe(POSITION, null), null));
    }

    @Test
    void droneTechnicianAndAdminSubscribeWithoutOrderLookup() {
        interceptor.preSend(subscribe(POSITION, new StompPrincipal("9", List.of("DRONE_TECHNICIAN"))), null);
        interceptor.preSend(subscribe(POSITION, new StompPrincipal("1", List.of("ADMIN"))), null);

        assertEquals(0, lookups.get());
    }

    @Test
    void lookupFailureRefusesSubscription() {
        DeliveryTopicSubscriptionInterceptor failing = new DeliveryTopicSubscriptionInterceptor(orderId -> {
            throw new IllegalStateException("order-service down");
        });

        assertThrows(MessagingException.class, () -> failing.preSend(subscribe(POSITION, customer("44")), null));
    }

    @Test
    void wildcardSubscriptionOverDeliveriesIsRefusedForCustomers() {
        assertThrows(MessagingException.class,
                () -> interceptor.preSend(subscribe("/topic/deliveries/*/position", customer("44")), null));
        assertThrows(MessagingException.class,
                () -> interceptor.preSend(subscribe("/topic/**", customer("44")), null));
    }

    @Test
    void clientsCannotPublishToDeliveryTopics() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SEND);
        accessor.setDestination(POSITION);
        accessor.setUser(new StompPrincipal("1", List.of("ADMIN")));
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        assertThrows(MessagingException.class, () -> interceptor.preSend(message, null));
    }

    @Test
    void otherTopicsAreNotChecked() {
        Message<byte[]> message = subscribe("/topic/notifications", customer("77"));

        assertSame(message, interceptor.preSend(message, null));
        assertEquals(0, lookups.get());
    }

    private static StompPrincipal customer(String id) {
        return new StompPrincipal(id, List.of("CUSTOMER"));
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
}

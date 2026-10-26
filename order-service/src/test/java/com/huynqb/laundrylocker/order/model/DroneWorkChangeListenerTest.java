package com.huynqb.laundrylocker.order.model;

import com.huynqb.laundrylocker.common.event.DomainEvent;
import com.huynqb.laundrylocker.common.event.DomainEventNames;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.ObjectProvider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DroneWorkChangeListenerTest {

    private final RabbitTemplate rabbit = mock(RabbitTemplate.class);
    private final DroneWorkChangeListener listener = new DroneWorkChangeListener(provider(rabbit));

    @SuppressWarnings("unchecked")
    private static ObjectProvider<RabbitTemplate> provider(RabbitTemplate rabbit) {
        ObjectProvider<RabbitTemplate> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(rabbit);
        return provider;
    }

    @Test
    void droneOrderChangeIsAnnouncedToStaff() {
        LockerOrder order = new LockerOrder();
        order.setId(21L);
        order.setType("DRONE_DELIVERY");

        listener.changed(order);

        ArgumentCaptor<Object> event = ArgumentCaptor.forClass(Object.class);
        verify(rabbit).convertAndSend(eq(DomainEventNames.EXCHANGE), eq(DomainEventNames.MAINTENANCE_CHANGED),
                event.capture());
        DomainEvent sent = (DomainEvent) event.getValue();
        assertEquals("DRONE_ORDER", sent.payload().get("entity"));
        assertEquals(21L, sent.payload().get("id"));
    }

    /// Đơn tủ thường (gửi hàng, thuê ô) không phải việc của KTV drone ⇒ không phát lên topic nhân viên.
    @Test
    void lockerOrdersAreNotAnnounced() {
        LockerOrder order = new LockerOrder();
        order.setId(22L);
        order.setType("SEND");

        listener.changed(order);

        verify(rabbit, never()).convertAndSend(anyString(), anyString(), any(Object.class));
    }
}

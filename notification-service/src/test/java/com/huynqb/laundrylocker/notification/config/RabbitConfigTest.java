package com.huynqb.laundrylocker.notification.config;

import com.huynqb.laundrylocker.common.event.DomainEvent;
import com.huynqb.laundrylocker.common.event.DomainEventNames;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.support.converter.MessageConverter;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class RabbitConfigTest {

    @Test
    void domainEventMessageConverterAllowsProjectDomainEvents() {
        MessageConverter converter = new RabbitConfig().domainEventMessageConverter();
        DomainEvent event =
                DomainEvent.of(
                        DomainEventNames.ORDER_STATUS_CHANGED,
                        "order-service",
                        Map.of("userId", 7L, "orderId", 123L));

        Message message = converter.toMessage(event, new MessageProperties());
        Object converted = converter.fromMessage(message);

        DomainEvent convertedEvent = assertInstanceOf(DomainEvent.class, converted);
        assertEquals(event.type(), convertedEvent.type());
        assertEquals(event.payload().get("userId"), convertedEvent.payload().get("userId"));
    }
}

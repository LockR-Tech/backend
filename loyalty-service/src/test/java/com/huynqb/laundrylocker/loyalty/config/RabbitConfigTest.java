package com.huynqb.laundrylocker.loyalty.config;

import com.huynqb.laundrylocker.common.event.DomainEvent;
import com.huynqb.laundrylocker.common.event.DomainEventNames;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.support.converter.MessageConverter;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class RabbitConfigTest {

    @Test
    void loyaltyQueueListensToOrderCompleted() {
        RabbitConfig config = new RabbitConfig();

        Binding binding = config.loyaltyOrderCompletedBinding(config.loyaltyOrderEventsQueue(), config.laundryEventsExchange());

        assertEquals(DomainEventNames.ORDER_COMPLETED, binding.getRoutingKey());
        assertEquals(RabbitConfig.LOYALTY_ORDER_QUEUE, binding.getDestination());
        assertEquals(DomainEventNames.EXCHANGE, binding.getExchange());
    }

    @Test
    void converterReadsOrderEventsWithAmounts() {
        MessageConverter converter = new RabbitConfig().domainEventMessageConverter();
        DomainEvent event = DomainEvent.of(DomainEventNames.ORDER_COMPLETED, "order-service",
                Map.of("orderId", 55L, "userId", 44L, "paymentStatus", "PAID", "paidAmount", new BigDecimal("25000")));

        Message message = converter.toMessage(event, new MessageProperties());
        DomainEvent read = assertInstanceOf(DomainEvent.class, converter.fromMessage(message));

        assertEquals(new BigDecimal("25000"), read.payload().get("paidAmount"));
        assertEquals(55L, read.payload().get("orderId"));
    }
}

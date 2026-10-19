package com.huynqb.laundrylocker.loyalty.config;

import com.huynqb.laundrylocker.common.event.DomainEventNames;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.amqp.support.converter.SimpleMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/// Hàng đợi riêng của loyalty-service gắn vào exchange chung `laundry.events` với routing key
/// `order.completed` (order-service phát một lần khi đơn vừa chuyển sang COMPLETED) — dùng để cộng điểm —
/// và `payment.refunded` (admin xác nhận đã hoàn tiền) — dùng để thu hồi điểm đã tích. Một hàng đợi,
/// một listener ({@link com.huynqb.laundrylocker.loyalty.service.OrderCompletedPointsListener}).
@Configuration
public class RabbitConfig {

    public static final String LOYALTY_ORDER_QUEUE = "loyalty.order.events";

    @Bean
    TopicExchange laundryEventsExchange() {
        return new TopicExchange(DomainEventNames.EXCHANGE, true, false);
    }

    @Bean
    Queue loyaltyOrderEventsQueue() {
        return new Queue(LOYALTY_ORDER_QUEUE, true);
    }

    @Bean
    Binding loyaltyOrderCompletedBinding(Queue loyaltyOrderEventsQueue, TopicExchange laundryEventsExchange) {
        return BindingBuilder.bind(loyaltyOrderEventsQueue)
                .to(laundryEventsExchange)
                .with(DomainEventNames.ORDER_COMPLETED);
    }

    @Bean
    Binding loyaltyPaymentRefundedBinding(Queue loyaltyOrderEventsQueue, TopicExchange laundryEventsExchange) {
        return BindingBuilder.bind(loyaltyOrderEventsQueue)
                .to(laundryEventsExchange)
                .with(DomainEventNames.PAYMENT_REFUNDED);
    }

    /// order-service gửi DomainEvent bằng Java serialization (SimpleMessageConverter) — chỉ cho phép các lớp
    /// của sự kiện; payload `order.completed` có số tiền BigDecimal nên cần java.math.
    @Bean
    MessageConverter domainEventMessageConverter() {
        SimpleMessageConverter converter = new SimpleMessageConverter();
        converter.setAllowedListPatterns(
                List.of(
                        "com.huynqb.laundrylocker.common.event.*",
                        "java.lang.*",
                        "java.math.*",
                        "java.time.*",
                        "java.util.*"));
        return converter;
    }
}

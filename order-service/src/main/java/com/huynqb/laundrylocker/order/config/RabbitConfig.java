package com.huynqb.laundrylocker.order.config;

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

@Configuration
public class RabbitConfig {

    public static final String ORDER_PAYMENT_QUEUE = "order.payment.events";

    @Bean
    TopicExchange laundryEventsExchange() {
        return new TopicExchange(DomainEventNames.EXCHANGE, true, false);
    }

    @Bean
    Queue orderPaymentEventsQueue() {
        return new Queue(ORDER_PAYMENT_QUEUE, true);
    }

    @Bean
    Binding orderPaymentCompletedBinding(
            Queue orderPaymentEventsQueue, TopicExchange laundryEventsExchange) {
        return BindingBuilder.bind(orderPaymentEventsQueue)
                .to(laundryEventsExchange)
                .with(DomainEventNames.PAYMENT_COMPLETED);
    }

    @Bean
    Binding orderPaymentFailedBinding(
            Queue orderPaymentEventsQueue, TopicExchange laundryEventsExchange) {
        return BindingBuilder.bind(orderPaymentEventsQueue)
                .to(laundryEventsExchange)
                .with(DomainEventNames.PAYMENT_FAILED);
    }

    @Bean
    MessageConverter domainEventMessageConverter() {
        SimpleMessageConverter converter = new SimpleMessageConverter();
        converter.setAllowedListPatterns(
                List.of(
                        "com.huynqb.laundrylocker.common.event.*",
                        "java.lang.*",
                        "java.time.*",
                        "java.util.*"));
        return converter;
    }
}

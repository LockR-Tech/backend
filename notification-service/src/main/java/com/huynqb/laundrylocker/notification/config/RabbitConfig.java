package com.huynqb.laundrylocker.notification.config;

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

    public static final String NOTIFICATION_QUEUE = "notification.events";

    @Bean
    TopicExchange laundryEventsExchange() {
        return new TopicExchange(DomainEventNames.EXCHANGE, true, false);
    }

    @Bean
    Queue notificationEventsQueue() {
        return new Queue(NOTIFICATION_QUEUE, true);
    }

    @Bean
    Binding orderStatusChangedBinding(Queue notificationEventsQueue, TopicExchange laundryEventsExchange) {
        return BindingBuilder.bind(notificationEventsQueue).to(laundryEventsExchange).with(DomainEventNames.ORDER_STATUS_CHANGED);
    }

    @Bean
    Binding paymentCompletedBinding(Queue notificationEventsQueue, TopicExchange laundryEventsExchange) {
        return BindingBuilder.bind(notificationEventsQueue).to(laundryEventsExchange).with(DomainEventNames.PAYMENT_COMPLETED);
    }

    @Bean
    Binding paymentFailedBinding(Queue notificationEventsQueue, TopicExchange laundryEventsExchange) {
        return BindingBuilder.bind(notificationEventsQueue).to(laundryEventsExchange).with(DomainEventNames.PAYMENT_FAILED);
    }

    @Bean
    Binding reportClaimedBinding(Queue notificationEventsQueue, TopicExchange laundryEventsExchange) {
        return BindingBuilder.bind(notificationEventsQueue).to(laundryEventsExchange).with(DomainEventNames.LOCKER_REPORT_CLAIMED);
    }

    @Bean
    Binding reportResolvedBinding(Queue notificationEventsQueue, TopicExchange laundryEventsExchange) {
        return BindingBuilder.bind(notificationEventsQueue).to(laundryEventsExchange).with(DomainEventNames.LOCKER_REPORT_RESOLVED);
    }

    @Bean
    Binding deliveryStatusChangedBinding(Queue notificationEventsQueue, TopicExchange laundryEventsExchange) {
        return BindingBuilder.bind(notificationEventsQueue).to(laundryEventsExchange).with(DomainEventNames.DELIVERY_STATUS_CHANGED);
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

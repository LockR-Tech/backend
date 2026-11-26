package com.huynqb.laundrylocker.user.config;

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

/// Hàng đợi nhật ký thao tác (F3-G16): mọi service phát `admin.action.logged`, user-service lưu.
@Configuration
public class ActionLogRabbitConfig {

    public static final String ACTION_LOG_QUEUE = "user.action-logs";

    @Bean
    TopicExchange laundryEventsExchange() {
        return new TopicExchange(DomainEventNames.EXCHANGE, true, false);
    }

    @Bean
    Queue actionLogQueue() {
        return new Queue(ACTION_LOG_QUEUE, true);
    }

    @Bean
    Binding actionLogBinding(Queue actionLogQueue, TopicExchange laundryEventsExchange) {
        return BindingBuilder.bind(actionLogQueue).to(laundryEventsExchange).with(DomainEventNames.ADMIN_ACTION_LOGGED);
    }

    /// Các service gửi DomainEvent bằng Java serialization — chỉ cho phép lớp của sự kiện và kiểu JDK cơ bản.
    @Bean
    MessageConverter domainEventMessageConverter() {
        SimpleMessageConverter converter = new SimpleMessageConverter();
        converter.setAllowedListPatterns(List.of(
                "com.huynqb.laundrylocker.common.event.*",
                "java.lang.*",
                "java.math.*",
                "java.time.*",
                "java.util.*"));
        return converter;
    }
}

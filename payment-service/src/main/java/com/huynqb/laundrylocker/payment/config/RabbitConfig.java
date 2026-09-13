package com.huynqb.laundrylocker.payment.config;

import com.huynqb.laundrylocker.common.event.DomainEventNames;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {

    @Bean
    TopicExchange laundryEventsExchange() {
        return new TopicExchange(DomainEventNames.EXCHANGE, true, false);
    }
}

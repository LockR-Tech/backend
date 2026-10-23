package com.huynqb.laundrylocker.order.service;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.web.client.RestClientAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/// DroneWeatherService có nhiều constructor; thiếu @Autowired trên constructor @Value khiến Spring
/// rơi về constructor rỗng (không có) ⇒ context order-service không khởi động được (503). Test này
/// khởi động một context nhỏ để chặn lỗi đó tái diễn.
class DroneWeatherServiceContextTest {

    @Test
    void weatherServiceBeanStartsInSpringContext() {
        new ApplicationContextRunner()
                .withConfiguration(org.springframework.boot.autoconfigure.AutoConfigurations.of(RestClientAutoConfiguration.class))
                .withBean(DroneWeatherService.class)
                .run(context -> assertThat(context)
                        .hasNotFailed()
                        .hasSingleBean(DroneWeatherService.class));
    }
}

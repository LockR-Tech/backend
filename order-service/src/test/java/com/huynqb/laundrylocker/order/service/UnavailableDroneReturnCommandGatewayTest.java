package com.huynqb.laundrylocker.order.service;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class UnavailableDroneReturnCommandGatewayTest {

    @Test
    void registersFallbackGatewayBean() {
        new ApplicationContextRunner()
                .withUserConfiguration(UnavailableDroneReturnCommandGateway.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(DroneReturnCommandGateway.class);
                    assertThat(context.getBean(DroneReturnCommandGateway.class))
                            .isInstanceOf(UnavailableDroneReturnCommandGateway.class);
                });
    }
}

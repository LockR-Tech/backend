package com.huynqb.laundrylocker.iot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableFeignClients
@EnableScheduling
@SpringBootApplication(scanBasePackages = {"com.huynqb.laundrylocker.iot", "com.huynqb.laundrylocker.common"})
public class IotServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(IotServiceApplication.class, args);
    }
}

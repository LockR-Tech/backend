package com.huynqb.laundrylocker.locker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableFeignClients
@EnableScheduling
@SpringBootApplication(scanBasePackages = {"com.huynqb.laundrylocker.locker", "com.huynqb.laundrylocker.common"})
public class LockerServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(LockerServiceApplication.class, args);
    }
}

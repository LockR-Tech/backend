package com.huynqb.laundrylocker.order.repository;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;

/// Cấu hình tối thiểu cho @DataJpaTest: tránh nạp OrderServiceApplication
/// (@EnableFeignClients, @EnableScheduling, component scan common-lib) trong slice JPA.
@SpringBootConfiguration
@EnableAutoConfiguration
@EntityScan("com.huynqb.laundrylocker.order.model")
class JpaSliceTestConfig {
}

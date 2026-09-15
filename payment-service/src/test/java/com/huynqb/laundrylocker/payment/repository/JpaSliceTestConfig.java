package com.huynqb.laundrylocker.payment.repository;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;

/// Cấu hình tối thiểu cho @DataJpaTest: tránh nạp PaymentServiceApplication
/// (@EnableFeignClients, component scan common-lib) trong slice JPA.
@SpringBootConfiguration
@EnableAutoConfiguration
@EntityScan("com.huynqb.laundrylocker.payment.model")
class JpaSliceTestConfig {
}

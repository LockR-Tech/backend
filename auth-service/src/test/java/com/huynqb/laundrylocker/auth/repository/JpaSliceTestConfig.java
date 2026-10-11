package com.huynqb.laundrylocker.auth.repository;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;

/// Cấu hình tối thiểu cho @DataJpaTest: tránh nạp AuthServiceApplication
/// (@EnableFeignClients, component scan common-lib) trong slice JPA.
@SpringBootConfiguration
@EnableAutoConfiguration
@EntityScan("com.huynqb.laundrylocker.auth.model")
class JpaSliceTestConfig {
}

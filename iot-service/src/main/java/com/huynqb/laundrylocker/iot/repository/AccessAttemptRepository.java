package com.huynqb.laundrylocker.iot.repository;

import com.huynqb.laundrylocker.iot.model.AccessAttempt;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccessAttemptRepository extends JpaRepository<AccessAttempt, Long> {
}

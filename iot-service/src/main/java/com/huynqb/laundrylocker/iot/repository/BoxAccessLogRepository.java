package com.huynqb.laundrylocker.iot.repository;

import com.huynqb.laundrylocker.iot.model.BoxAccessLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BoxAccessLogRepository extends JpaRepository<BoxAccessLog, Long> {

    List<BoxAccessLog> findByLockerIdOrderByCreatedAtDesc(Long lockerId);
}

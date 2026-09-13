package com.huynqb.laundrylocker.locker.repository;

import com.huynqb.laundrylocker.locker.model.RepairLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RepairLogRepository extends JpaRepository<RepairLog, Long> {

    List<RepairLog> findByReportIdOrderByCreatedAtAsc(Long reportId);
}

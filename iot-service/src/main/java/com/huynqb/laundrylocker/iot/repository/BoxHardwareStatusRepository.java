package com.huynqb.laundrylocker.iot.repository;

import com.huynqb.laundrylocker.iot.model.BoxHardwareStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BoxHardwareStatusRepository extends JpaRepository<BoxHardwareStatus, Long> {

    List<BoxHardwareStatus> findAllByOrderByLastReportedAtDesc();

    List<BoxHardwareStatus> findByLockerIdOrderByLastReportedAtDesc(Long lockerId);
}

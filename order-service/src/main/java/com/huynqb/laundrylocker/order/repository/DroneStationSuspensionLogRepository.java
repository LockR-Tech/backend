package com.huynqb.laundrylocker.order.repository;

import com.huynqb.laundrylocker.order.model.DroneStationSuspensionLog;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DroneStationSuspensionLogRepository extends JpaRepository<DroneStationSuspensionLog, Long> {

    List<DroneStationSuspensionLog> findAllByOrderByCreatedAtDescIdDesc(Pageable pageable);

    List<DroneStationSuspensionLog> findByLockerIdOrderByCreatedAtDescIdDesc(Long lockerId, Pageable pageable);
}

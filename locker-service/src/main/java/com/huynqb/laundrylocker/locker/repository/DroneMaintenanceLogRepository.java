package com.huynqb.laundrylocker.locker.repository;

import com.huynqb.laundrylocker.locker.model.DroneMaintenanceLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DroneMaintenanceLogRepository extends JpaRepository<DroneMaintenanceLog, Long> {

    List<DroneMaintenanceLog> findByDroneUnitIdOrderByCreatedAtAsc(Long droneUnitId);
}

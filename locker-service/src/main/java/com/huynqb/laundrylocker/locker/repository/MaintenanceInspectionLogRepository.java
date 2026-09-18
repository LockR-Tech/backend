package com.huynqb.laundrylocker.locker.repository;

import com.huynqb.laundrylocker.locker.model.MaintenanceInspectionLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MaintenanceInspectionLogRepository extends JpaRepository<MaintenanceInspectionLog, Long> {

    List<MaintenanceInspectionLog> findByScheduleIdOrderByCreatedAtDesc(Long scheduleId);

    List<MaintenanceInspectionLog> findByLockerIdOrderByCreatedAtDesc(Long lockerId);

    List<MaintenanceInspectionLog> findByTechnicianIdOrderByCreatedAtDesc(Long technicianId);

    List<MaintenanceInspectionLog> findAllByOrderByCreatedAtDesc();
}

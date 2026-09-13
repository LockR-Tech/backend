package com.huynqb.laundrylocker.locker.repository;

import com.huynqb.laundrylocker.locker.model.MaintenanceSchedule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MaintenanceScheduleRepository extends JpaRepository<MaintenanceSchedule, Long> {

    List<MaintenanceSchedule> findByActiveTrueOrderByNextDueAtAsc();
}

package com.huynqb.laundrylocker.locker.repository;

import com.huynqb.laundrylocker.locker.model.MaintenanceSchedule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface MaintenanceScheduleRepository extends JpaRepository<MaintenanceSchedule, Long> {

    List<MaintenanceSchedule> findByActiveTrueOrderByNextDueAtAsc();

    List<MaintenanceSchedule> findByPendingReportId(Long pendingReportId);

    /// Lịch sắp/đã tới hạn mà kỳ hiện tại chưa được nhắc.
    List<MaintenanceSchedule> findByActiveTrueAndLastDueNotifiedAtIsNullAndNextDueAtBefore(LocalDateTime cutoff);

    /// Đánh dấu đã nhắc có điều kiện — nhiều instance cùng chạy job thì chỉ một bên nhắc.
    @Modifying
    @Query("update MaintenanceSchedule s set s.lastDueNotifiedAt = :now "
            + "where s.id = :id and s.lastDueNotifiedAt is null")
    int markDueNotified(@Param("id") Long id, @Param("now") LocalDateTime now);
}

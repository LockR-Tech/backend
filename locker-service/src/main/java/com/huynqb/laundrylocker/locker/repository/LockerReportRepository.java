package com.huynqb.laundrylocker.locker.repository;

import com.huynqb.laundrylocker.locker.model.LockerReport;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LockerReportRepository extends JpaRepository<LockerReport, Long> {

    List<LockerReport> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<LockerReport> findByStatusInOrderByCreatedAtDesc(List<String> statuses);

    List<LockerReport> findByAssignedToUserIdOrderByCreatedAtDesc(Long assignedToUserId);

    Optional<LockerReport> findFirstByBoxIdAndStatusInOrderByCreatedAtDesc(Long boxId, List<String> statuses);

    Optional<LockerReport> findFirstByDroneUnitIdAndStatusInOrderByCreatedAtDesc(
            Long droneUnitId, List<String> statuses);

    long countByLockerIdAndStatusIn(Long lockerId, List<String> statuses);
}

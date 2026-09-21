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

    List<LockerReport> findByRoutedToUserIdAndStatusOrderByCreatedAtDesc(Long routedToUserId, String status);

    List<LockerReport> findByLockerIdAndStatusAndAssignedToUserIdIsNull(Long lockerId, String status);

    Optional<LockerReport> findFirstByLockerIdAndCategoryAndStatusInOrderByCreatedAtDesc(
            Long lockerId, String category, List<String> statuses);

    // Còn phiếu mở KHÁC trên cùng tài sản ⇒ chưa được khôi phục khi đóng một phiếu.
    boolean existsByBoxIdAndStatusInAndIdNot(Long boxId, List<String> statuses, Long id);

    boolean existsByLockerIdAndCategoryAndStatusInAndIdNot(
            Long lockerId, String category, List<String> statuses, Long id);

    boolean existsByLockerIdAndBlocksLockerTrueAndStatusInAndIdNot(Long lockerId, List<String> statuses, Long id);
}

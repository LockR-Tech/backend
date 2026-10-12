package com.huynqb.laundrylocker.order.repository;

import com.huynqb.laundrylocker.order.model.DroneParcelIncident;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface DroneParcelIncidentRepository extends JpaRepository<DroneParcelIncident, Long> {
    Optional<DroneParcelIncident> findByIncidentCode(String incidentCode);
    Optional<DroneParcelIncident> findByOrderId(Long orderId);
    Optional<DroneParcelIncident> findByIdempotencyKey(String idempotencyKey);
    List<DroneParcelIncident> findAllByOrderByCreatedAtDesc();
    List<DroneParcelIncident> findByReportedByUserIdOrRecoveryAssignedToUserIdOrderByCreatedAtDesc(
            Long reportedByUserId, Long recoveryAssignedToUserId);
    List<DroneParcelIncident> findByRecoveryAssignedToUserIdOrderByCreatedAtDesc(Long userId);

    /// Sự cố liên quan tới một KTV drone: người báo, người được giao thu hồi, hoặc người đã tiếp
    /// nhận chuyến bay có sự cố.
    @Query("select i from DroneParcelIncident i where i.reportedByUserId = :userId"
            + " or i.recoveryAssignedToUserId = :userId"
            + " or i.missionId in (select m.id from DroneMission m where m.assignedByUserId = :userId)"
            + " order by i.createdAt desc, i.id desc")
    List<DroneParcelIncident> findVisibleToDroneTechnician(@Param("userId") Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from DroneParcelIncident i where i.id = :id")
    Optional<DroneParcelIncident> findByIdForUpdate(@Param("id") Long id);
}


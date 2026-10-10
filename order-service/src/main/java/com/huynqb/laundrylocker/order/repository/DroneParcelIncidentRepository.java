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

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from DroneParcelIncident i where i.id = :id")
    Optional<DroneParcelIncident> findByIdForUpdate(@Param("id") Long id);
}


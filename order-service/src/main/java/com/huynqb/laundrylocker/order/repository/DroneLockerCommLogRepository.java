package com.huynqb.laundrylocker.order.repository;

import com.huynqb.laundrylocker.order.model.DroneLockerCommLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface DroneLockerCommLogRepository extends JpaRepository<DroneLockerCommLog, Long> {

    List<DroneLockerCommLog> findBySessionIdOrderByTimestampAsc(String sessionId);

    List<DroneLockerCommLog> findByOrderIdOrderByTimestampAsc(Long orderId);

    @Query("""
        SELECT l FROM DroneLockerCommLog l
        WHERE (:orderId IS NULL OR l.orderId = :orderId)
          AND (:droneId IS NULL OR l.droneId = :droneId)
          AND (:lockerId IS NULL OR l.lockerId = :lockerId)
          AND (:compartmentId IS NULL OR l.compartmentId = :compartmentId)
          AND (:eventType IS NULL OR l.eventType = :eventType)
          AND (:processingStatus IS NULL OR l.processingStatus = :processingStatus)
          AND (:fromDate IS NULL OR l.timestamp >= :fromDate)
          AND (:toDate IS NULL OR l.timestamp <= :toDate)
        ORDER BY l.timestamp DESC
    """)
    Page<DroneLockerCommLog> searchLogs(
            @Param("orderId") Long orderId,
            @Param("droneId") String droneId,
            @Param("lockerId") Long lockerId,
            @Param("compartmentId") Long compartmentId,
            @Param("eventType") String eventType,
            @Param("processingStatus") String processingStatus,
            @Param("fromDate") LocalDateTime fromDate,
            @Param("toDate") LocalDateTime toDate,
            Pageable pageable);

    @Query("SELECT DISTINCT l.sessionId FROM DroneLockerCommLog l WHERE l.sessionId IS NOT NULL ORDER BY l.sessionId DESC")
    List<String> findRecentSessionIds(Pageable pageable);
}


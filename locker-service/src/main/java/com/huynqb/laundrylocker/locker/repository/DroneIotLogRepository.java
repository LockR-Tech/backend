package com.huynqb.laundrylocker.locker.repository;

import com.huynqb.laundrylocker.locker.model.DroneIotLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface DroneIotLogRepository extends JpaRepository<DroneIotLog, Long> {

    List<DroneIotLog> findByDroneIdOrderByCreatedAtDesc(Long droneId);

    @Query("SELECT l FROM DroneIotLog l WHERE l.droneId = :droneId " +
           "AND (:component IS NULL OR l.component = :component) " +
           "AND (:eventType IS NULL OR l.eventType = :eventType) " +
           "AND (:result IS NULL OR l.result = :result) " +
           "AND (:search IS NULL OR :search = '' " +
           "     OR LOWER(l.message) LIKE CONCAT('%', LOWER(CAST(:search AS string)), '%') " +
           "     OR LOWER(l.actorId) LIKE CONCAT('%', LOWER(CAST(:search AS string)), '%') " +
           "     OR (l.orderId IS NOT NULL AND CAST(l.orderId AS string) LIKE CONCAT('%', CAST(:search AS string), '%'))) " +
           "ORDER BY l.createdAt DESC")
    List<DroneIotLog> searchLogs(
            @Param("droneId") Long droneId,
            @Param("component") String component,
            @Param("eventType") String eventType,
            @Param("result") String result,
            @Param("search") String search
    );
}

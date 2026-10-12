package com.huynqb.laundrylocker.order.repository;

import com.huynqb.laundrylocker.order.model.DronePickup;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface DronePickupRepository extends JpaRepository<DronePickup, Long> {

    Optional<DronePickup> findByOrderId(Long orderId);

    List<DronePickup> findByOrderIdIn(Collection<Long> orderIds);

    List<DronePickup> findByStatusInOrderByCreatedAtAsc(Collection<String> statuses);

    List<DronePickup> findByTechnicianIdAndStatusInOrderByCreatedAtDesc(Long technicianId, Collection<String> statuses);

    boolean existsByTechnicianIdAndStatusIn(Long technicianId, Collection<String> statuses);

    /// Khoá dòng: hai KTV cùng bấm nhận, hoặc job hết hạn chạy trùng lúc KTV bấm, không được chạy xen.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from DronePickup p where p.orderId = :orderId")
    Optional<DronePickup> findByOrderIdForUpdate(@Param("orderId") Long orderId);
}

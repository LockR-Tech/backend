package com.huynqb.laundrylocker.order.repository;

import com.huynqb.laundrylocker.order.model.LockerOrder;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface LockerOrderRepository extends JpaRepository<LockerOrder, Long>, JpaSpecificationExecutor<LockerOrder> {

    List<LockerOrder> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<LockerOrder> findByStatusOrderByCreatedAtDesc(String status);

    List<LockerOrder> findByStaffIdOrderByCreatedAtDesc(Long staffId);

    Optional<LockerOrder> findByOrderCode(String orderCode);

    Optional<LockerOrder> findByPinCode(String pinCode);

    Optional<LockerOrder> findByUserIdAndIdempotencyKey(Long userId, String idempotencyKey);

    List<LockerOrder> findByTypeAndStatusOrderByCreatedAtAsc(String type, String status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from LockerOrder o where o.id = :id")
    Optional<LockerOrder> findByIdForUpdate(@Param("id") Long id);
}

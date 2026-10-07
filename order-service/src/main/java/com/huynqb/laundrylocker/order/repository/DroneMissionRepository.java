package com.huynqb.laundrylocker.order.repository;

import com.huynqb.laundrylocker.order.model.DroneMission;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DroneMissionRepository extends JpaRepository<DroneMission, Long> {

    Optional<DroneMission> findByOrderId(Long orderId);

    List<DroneMission> findByStatusIn(List<String> statuses);

    List<DroneMission> findByOrderIdIn(java.util.Collection<Long> orderIds);

    /// Nhiệm vụ đang bay của một drone — telemetry chỉ biết mã drone, không biết đơn.
    Optional<DroneMission> findFirstByDroneCodeAndStatusInOrderByIdDesc(String droneCode, List<String> statuses);
}

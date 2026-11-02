package com.huynqb.laundrylocker.order.repository;

import com.huynqb.laundrylocker.order.model.DroneFlightTrackPoint;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DroneFlightTrackPointRepository extends JpaRepository<DroneFlightTrackPoint, Long> {

    /// Cũ → mới, hai điểm cùng thời điểm theo thứ tự ghi.
    List<DroneFlightTrackPoint> findByOrderIdOrderByRecordedAtAscIdAsc(Long orderId);
}

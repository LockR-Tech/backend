package com.huynqb.laundrylocker.order.repository;

import com.huynqb.laundrylocker.order.model.DroneDeliverySession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface DroneDeliverySessionRepository extends JpaRepository<DroneDeliverySession, String> {

    Optional<DroneDeliverySession> findByOpenCommandId(String openCommandId);

    Optional<DroneDeliverySession> findByCloseCommandId(String closeCommandId);

    Optional<DroneDeliverySession> findFirstByOrderIdAndStateInOrderByCreatedAtDesc(Long orderId, Collection<String> states);
    Optional<DroneDeliverySession> findFirstByOrderIdOrderByCreatedAtDesc(Long orderId);

    Optional<DroneDeliverySession> findFirstByDroneIdAndStateOrderByCreatedAtDesc(String droneId, String state);

    List<DroneDeliverySession> findByStateInOrderByCreatedAtDesc(Collection<String> states);

    List<DroneDeliverySession> findByLockerIdAndStateIn(Long lockerId, Collection<String> states);
}

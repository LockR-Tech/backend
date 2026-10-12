package com.huynqb.laundrylocker.order.repository;

import com.huynqb.laundrylocker.order.model.DronePickupOffer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DronePickupOfferRepository extends JpaRepository<DronePickupOffer, Long> {

    List<DronePickupOffer> findByPickupId(Long pickupId);

    Optional<DronePickupOffer> findFirstByPickupIdAndTechnicianIdAndStatusOrderByIdDesc(
            Long pickupId, Long technicianId, String status);
}

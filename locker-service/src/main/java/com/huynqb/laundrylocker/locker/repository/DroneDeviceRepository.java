package com.huynqb.laundrylocker.locker.repository;

import com.huynqb.laundrylocker.locker.model.DroneDevice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DroneDeviceRepository extends JpaRepository<DroneDevice, Long> {

    Optional<DroneDevice> findByMacAddress(String macAddress);

    Optional<DroneDevice> findByDroneId(Long droneId);

    Optional<DroneDevice> findByDroneCode(String droneCode);

    Optional<DroneDevice> findByIpAddress(String ipAddress);

    List<DroneDevice> findAllByOrderByIdAsc();
}

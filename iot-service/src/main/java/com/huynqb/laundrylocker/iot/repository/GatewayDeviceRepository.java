package com.huynqb.laundrylocker.iot.repository;

import com.huynqb.laundrylocker.iot.model.GatewayDevice;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface GatewayDeviceRepository extends JpaRepository<GatewayDevice, Long> {

    Optional<GatewayDevice> findByMacAddress(String macAddress);

    Optional<GatewayDevice> findByLockerId(Long lockerId);

    List<GatewayDevice> findAllByOrderByIdAsc();
}

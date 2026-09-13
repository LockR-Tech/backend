package com.huynqb.laundrylocker.iot.repository;

import com.huynqb.laundrylocker.iot.model.DeviceStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DeviceStatusRepository extends JpaRepository<DeviceStatus, Long> {

    Optional<DeviceStatus> findByDeviceId(String deviceId);

    List<DeviceStatus> findByLockerId(Long lockerId);
}

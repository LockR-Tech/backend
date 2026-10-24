package com.huynqb.laundrylocker.iot.repository;

import com.huynqb.laundrylocker.iot.model.BleSigningKey;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BleSigningKeyRepository extends JpaRepository<BleSigningKey, Long> {
}

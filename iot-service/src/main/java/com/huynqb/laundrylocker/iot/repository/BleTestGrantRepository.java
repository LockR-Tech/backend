package com.huynqb.laundrylocker.iot.repository;

import com.huynqb.laundrylocker.iot.model.BleTestGrant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BleTestGrantRepository extends JpaRepository<BleTestGrant, Long> {

    List<BleTestGrant> findByLockerIdOrderByCreatedAtDesc(Long lockerId);

    List<BleTestGrant> findByUserIdOrderByLockerIdAsc(Long userId);

    Optional<BleTestGrant> findByLockerIdAndUserId(Long lockerId, Long userId);

    boolean existsByLockerIdAndUserId(Long lockerId, Long userId);
}

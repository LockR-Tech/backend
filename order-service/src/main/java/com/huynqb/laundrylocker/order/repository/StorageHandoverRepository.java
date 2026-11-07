package com.huynqb.laundrylocker.order.repository;

import com.huynqb.laundrylocker.order.model.StorageHandover;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface StorageHandoverRepository extends JpaRepository<StorageHandover, Long> {
    Optional<StorageHandover> findByOrderId(Long orderId);
}

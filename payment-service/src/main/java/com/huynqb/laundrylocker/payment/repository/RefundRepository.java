package com.huynqb.laundrylocker.payment.repository;

import com.huynqb.laundrylocker.payment.model.RefundRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RefundRepository extends JpaRepository<RefundRecord, Long> {

    List<RefundRecord> findByOrderId(Long orderId);
}

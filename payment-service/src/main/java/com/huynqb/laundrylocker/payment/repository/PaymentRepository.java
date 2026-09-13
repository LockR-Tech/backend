package com.huynqb.laundrylocker.payment.repository;

import com.huynqb.laundrylocker.payment.model.PaymentRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<PaymentRecord, Long> {

    List<PaymentRecord> findByOrderId(Long orderId);

    Optional<PaymentRecord> findByReferenceId(String referenceId);
}

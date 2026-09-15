package com.huynqb.laundrylocker.payment.repository;

import com.huynqb.laundrylocker.payment.model.PaymentRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<PaymentRecord, Long>, JpaSpecificationExecutor<PaymentRecord> {

    List<PaymentRecord> findByOrderId(Long orderId);

    Optional<PaymentRecord> findByReferenceId(String referenceId);

    List<PaymentRecord> findByOrderIdIn(Collection<Long> orderIds);
}

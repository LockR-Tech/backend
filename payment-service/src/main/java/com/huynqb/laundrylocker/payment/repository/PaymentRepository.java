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

    @org.springframework.data.jpa.repository.Query("SELECT COALESCE(SUM(p.amount), 0) FROM PaymentRecord p WHERE p.userId = :userId AND p.status = 'COMPLETED' AND (p.method LIKE 'SEPAY%' OR p.method LIKE 'VNPAY%') AND (p.orderId <= 0 OR p.method LIKE '%TOPUP')")
    java.math.BigDecimal sumRealTopupsByUserId(@org.springframework.data.repository.query.Param("userId") Long userId);
}

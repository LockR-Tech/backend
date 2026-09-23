package com.huynqb.laundrylocker.payment.repository;

import com.huynqb.laundrylocker.payment.model.RefundRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Collection;
import java.util.List;

public interface RefundRepository extends JpaRepository<RefundRecord, Long>, JpaSpecificationExecutor<RefundRecord> {

    List<RefundRecord> findByOrderId(Long orderId);

    List<RefundRecord> findByOrderIdIn(Collection<Long> orderIds);

    List<RefundRecord> findByPaymentIdIn(Collection<Long> paymentIds);

    @org.springframework.data.jpa.repository.Query("SELECT COALESCE(SUM(r.amount), 0) FROM RefundRecord r, PaymentRecord p WHERE r.paymentId = p.id AND p.userId = :userId AND r.status = 'COMPLETED' AND (p.method LIKE 'SEPAY%' OR p.method LIKE 'VNPAY%')")
    java.math.BigDecimal sumRealRefundsByUserId(@org.springframework.data.repository.query.Param("userId") Long userId);
}

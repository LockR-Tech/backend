package com.huynqb.laundrylocker.payment.repository;

import com.huynqb.laundrylocker.payment.model.PaymentRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<PaymentRecord, Long>, JpaSpecificationExecutor<PaymentRecord> {

    List<PaymentRecord> findByOrderId(Long orderId);

    List<PaymentRecord> findByUserIdOrderByCreatedAtDesc(Long userId);

    Optional<PaymentRecord> findByReferenceId(String referenceId);

    List<PaymentRecord> findByContentContaining(String content);

    List<PaymentRecord> findByOrderIdIn(Collection<Long> orderIds);

    /// Tiền nạp thật vào ví. VNPAY% chỉ còn ở giao dịch cũ (VNPay đã ngừng hỗ trợ) — vẫn tính để số dư rút được đúng.
    @org.springframework.data.jpa.repository.Query("SELECT COALESCE(SUM(p.amount), 0) FROM PaymentRecord p WHERE p.userId = :userId AND p.status = 'COMPLETED' AND (p.method LIKE 'SEPAY%' OR p.method LIKE 'VNPAY%') AND (p.orderId <= 0 OR p.method LIKE '%TOPUP')")
    java.math.BigDecimal sumRealTopupsByUserId(@org.springframework.data.repository.query.Param("userId") Long userId);
}

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

    /// Giao dịch đã ghi nhận theo mã giao dịch ngân hàng (chống webhook SePay gửi lại).
    Optional<PaymentRecord> findFirstByReferenceTransactionId(String referenceTransactionId);

    List<PaymentRecord> findByContentContaining(String content);

    List<PaymentRecord> findByOrderIdIn(Collection<Long> orderIds);

}

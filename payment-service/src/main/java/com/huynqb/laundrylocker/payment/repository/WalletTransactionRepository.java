package com.huynqb.laundrylocker.payment.repository;

import com.huynqb.laundrylocker.payment.model.WalletTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/// Chỉ đọc: biến động ví cũ để hiện trong lịch sử giao dịch của khách (ví Lock.R đã gỡ, không ghi mới).
public interface WalletTransactionRepository extends JpaRepository<WalletTransaction, Long> {

    List<WalletTransaction> findByUserIdOrderByCreatedAtDesc(Long userId);
}

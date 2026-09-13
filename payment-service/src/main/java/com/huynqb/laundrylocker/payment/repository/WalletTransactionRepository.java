package com.huynqb.laundrylocker.payment.repository;

import com.huynqb.laundrylocker.payment.model.WalletTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WalletTransactionRepository extends JpaRepository<WalletTransaction, Long> {
    List<WalletTransaction> findByUserIdOrderByCreatedAtDesc(Long userId);

    boolean existsBySourceAndReferenceId(String source, String referenceId);
}

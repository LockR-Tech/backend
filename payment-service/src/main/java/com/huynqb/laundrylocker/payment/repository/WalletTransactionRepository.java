package com.huynqb.laundrylocker.payment.repository;

import com.huynqb.laundrylocker.payment.model.WalletTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

public interface WalletTransactionRepository
        extends JpaRepository<WalletTransaction, Long>, JpaSpecificationExecutor<WalletTransaction> {
    List<WalletTransaction> findByUserIdOrderByCreatedAtDesc(Long userId);

    boolean existsBySourceAndReferenceId(String source, String referenceId);

    Optional<WalletTransaction> findFirstBySourceAndReferenceId(String source, String referenceId);
}

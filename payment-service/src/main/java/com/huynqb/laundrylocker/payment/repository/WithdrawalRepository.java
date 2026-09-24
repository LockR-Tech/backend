package com.huynqb.laundrylocker.payment.repository;

import com.huynqb.laundrylocker.payment.model.WithdrawalRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface WithdrawalRepository extends JpaRepository<WithdrawalRecord, Long> {

    List<WithdrawalRecord> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<WithdrawalRecord> findAllByOrderByCreatedAtDesc();

    List<WithdrawalRecord> findByStatusOrderByCreatedAtDesc(String status);

    Optional<WithdrawalRecord> findByReferenceId(String referenceId);

    @Query("SELECT COALESCE(SUM(w.amount), 0) FROM WithdrawalRecord w WHERE w.userId = :userId AND w.status != 'REJECTED'")
    BigDecimal sumActiveWithdrawalsByUserId(@Param("userId") Long userId);
}

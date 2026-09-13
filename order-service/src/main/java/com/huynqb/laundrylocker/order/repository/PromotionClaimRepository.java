package com.huynqb.laundrylocker.order.repository;

import com.huynqb.laundrylocker.order.model.PromotionClaim;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PromotionClaimRepository extends JpaRepository<PromotionClaim, Long> {

    Optional<PromotionClaim> findByPromotionIdAndUserId(Long promotionId, Long userId);

    List<PromotionClaim> findByUserIdOrderByCreatedAtDesc(Long userId);

    boolean existsByPromotionId(Long promotionId);
}

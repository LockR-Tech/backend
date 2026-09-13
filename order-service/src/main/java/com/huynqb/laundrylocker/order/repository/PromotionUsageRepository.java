package com.huynqb.laundrylocker.order.repository;

import com.huynqb.laundrylocker.order.model.PromotionUsage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PromotionUsageRepository extends JpaRepository<PromotionUsage, Long> {

    long countByPromotionIdAndUserId(Long promotionId, Long userId);

    List<PromotionUsage> findByOrderId(Long orderId);

    boolean existsByPromotionId(Long promotionId);
}

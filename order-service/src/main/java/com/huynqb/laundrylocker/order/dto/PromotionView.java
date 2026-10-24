package com.huynqb.laundrylocker.order.dto;

import com.huynqb.laundrylocker.order.dto.admin.LockerInfo;
import com.huynqb.laundrylocker.order.model.Promotion;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/// Mã giảm giá trả cho app/web: đủ các field JSON cũ của entity Promotion (client cũ không vỡ) + tủ áp
/// dụng. `lockerId`/`lockerCode`/`lockerName` null = toàn hệ thống; tra tên tủ lỗi thì code/name null.
/// `createdByUserId` chỉ có ở API admin.
public record PromotionView(
        Long id,
        String code,
        String name,
        String discountType,
        BigDecimal discountValue,
        BigDecimal maxDiscountAmount,
        BigDecimal minOrderAmount,
        Boolean stackable,
        Long lockerId,
        String lockerCode,
        String lockerName,
        Integer totalUsageLimit,
        Integer perUserLimit,
        String status,
        LocalDateTime startAt,
        LocalDateTime endAt,
        Integer usageCount,
        String imageUrl,
        String description,
        /// > 0 ⇒ voucher đổi bằng điểm.
        Integer pointsCost,
        Long createdByUserId,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    public static PromotionView of(Promotion promotion, LockerInfo locker, boolean admin) {
        return new PromotionView(
                promotion.getId(),
                promotion.getCode(),
                promotion.getName(),
                promotion.getDiscountType(),
                promotion.getDiscountValue(),
                promotion.getMaxDiscountAmount(),
                promotion.getMinOrderAmount(),
                promotion.getStackable(),
                promotion.getLockerId(),
                locker == null ? null : locker.code(),
                locker == null ? null : locker.name(),
                promotion.getTotalUsageLimit(),
                promotion.getPerUserLimit(),
                promotion.getStatus(),
                promotion.getStartAt(),
                promotion.getEndAt(),
                promotion.getUsageCount(),
                promotion.getImageUrl(),
                promotion.getDescription(),
                promotion.getPointsCost(),
                admin ? promotion.getCreatedByUserId() : null,
                promotion.getCreatedAt(),
                promotion.getUpdatedAt());
    }
}

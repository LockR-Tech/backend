package com.huynqb.laundrylocker.order.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record OrderResponse(
        Long id,
        String orderCode,
        Long userId,
        Long receiverId,
        Long lockerId,
        Long sendBoxId,
        Long receiveBoxId,
        Long storeId,
        Long staffId,
        String type,
        String serviceCategory,
        String status,
        String deliveryStage,
        String pinCode,
        String qrToken,
        BigDecimal actualWeight,
        String weightUnit,
        BigDecimal extraFee,
        BigDecimal discount,
        BigDecimal totalPrice,
        BigDecimal originalPrice,
        String promotionCode,
        String appliedPromotionCodes,
        String nextAction,
        String nextActionMessage,
        Boolean paymentRequired,
        String paymentStatus,
        Boolean overtime,
        LocalDateTime pickupDeadline,
        LocalDateTime returnedAt,
        LocalDateTime completedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        List<OrderDetailResponse> orderDetails) {
}

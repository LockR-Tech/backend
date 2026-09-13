package com.huynqb.laundrylocker.order.dto;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

public record CreateOrderRequest(
        @NotNull Long userId,
        Long lockerId,
        Long sendBoxId,
        Set<Long> boxIds,
        Long storeId,
        String type,
        String serviceCategory,
        Long receiverId,
        String receiverPhone,
        String receiverName,
        LocalDateTime intendedReceiveAt,
        BigDecimal estimatedWeight,
        String customerNote,
        String deliveryAddress,
        List<Long> serviceIds,
        List<OrderItemRequest> items,
        String promotionCode,
        List<String> promotionCodes,
        BigDecimal totalPrice) {
}

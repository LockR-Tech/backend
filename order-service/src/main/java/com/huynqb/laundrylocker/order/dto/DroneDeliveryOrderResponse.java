package com.huynqb.laundrylocker.order.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record DroneDeliveryOrderResponse(
        Long orderId,
        String orderCode,
        Long userId,
        Long receiverUserId,
        Long destinationLockerId,
        Long reservedBoxId,
        String type,
        String status,
        String deliveryStage,
        String paymentStatus,
        Integer parcelWeightGrams,
        String description,
        BigDecimal totalPrice,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        String fulfillmentMode,
        Long missionId,
        String missionStatus,
        Long droneUnitId,
        String droneCode,
        Long sourceLockerId,
        Integer etaMinutes) {
}

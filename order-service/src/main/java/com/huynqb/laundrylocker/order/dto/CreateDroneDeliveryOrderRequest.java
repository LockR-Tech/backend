package com.huynqb.laundrylocker.order.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateDroneDeliveryOrderRequest(
        @NotNull Long sourceLockerId,
        @NotNull Long destinationLockerId,
        Long preferredBoxId,
        String description,
        @NotNull Integer parcelWeightGrams,
        @NotBlank String paymentMethod,
        String fulfillmentMode,
        /// Ô DRONE người gửi chọn tại tủ gửi. Bỏ trống thì hệ thống tự lấy một ô DRONE
        /// còn trống của tủ gửi.
        Long sourceBoxId,
        /// Người nhận tại tủ đích. Bỏ trống cả hai ⇒ người đặt tự nhận.
        @jakarta.validation.constraints.Size(max = 20) String receiverPhone,
        @jakarta.validation.constraints.Size(max = 120) String receiverName) {

    public CreateDroneDeliveryOrderRequest(
            Long sourceLockerId,
            Long destinationLockerId,
            Long preferredBoxId,
            String description,
            Integer parcelWeightGrams,
            String paymentMethod,
            String fulfillmentMode,
            Long sourceBoxId) {
        this(sourceLockerId, destinationLockerId, preferredBoxId, description, parcelWeightGrams,
                paymentMethod, fulfillmentMode, sourceBoxId, null, null);
    }

    public CreateDroneDeliveryOrderRequest(
            Long sourceLockerId,
            Long destinationLockerId,
            Long preferredBoxId,
            String description,
            Integer parcelWeightGrams,
            String paymentMethod,
            String fulfillmentMode) {
        this(sourceLockerId, destinationLockerId, preferredBoxId, description, parcelWeightGrams,
                paymentMethod, fulfillmentMode, null, null, null);
    }

    public CreateDroneDeliveryOrderRequest(
            Long sourceLockerId,
            Long destinationLockerId,
            Long preferredBoxId,
            String description,
            Integer parcelWeightGrams,
            String paymentMethod) {
        this(sourceLockerId, destinationLockerId, preferredBoxId, description, parcelWeightGrams, paymentMethod,
                null, null, null, null);
    }
}

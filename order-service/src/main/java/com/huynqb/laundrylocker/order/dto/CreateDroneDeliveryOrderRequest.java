package com.huynqb.laundrylocker.order.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateDroneDeliveryOrderRequest(
        @NotNull Long destinationLockerId,
        Long preferredBoxId,
        String description,
        @NotNull Integer parcelWeightGrams,
        @NotBlank String paymentMethod,
        String fulfillmentMode) {

    public CreateDroneDeliveryOrderRequest(
            Long destinationLockerId,
            Long preferredBoxId,
            String description,
            Integer parcelWeightGrams,
            String paymentMethod) {
        this(destinationLockerId, preferredBoxId, description, parcelWeightGrams, paymentMethod, null);
    }
}

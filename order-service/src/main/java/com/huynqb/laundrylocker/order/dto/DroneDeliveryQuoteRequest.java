package com.huynqb.laundrylocker.order.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/// Hỏi trước khi đặt: từ điểm lấy hàng này giao tới tủ kia thì đi qua tủ gửi nào, phí bao nhiêu.
public record DroneDeliveryQuoteRequest(
        @NotNull Long destinationLockerId,
        @NotNull @DecimalMin("-90") @DecimalMax("90") Double pickupLatitude,
        @NotNull @DecimalMin("-180") @DecimalMax("180") Double pickupLongitude,
        @NotNull @Min(1) Integer parcelWeightGrams) {
}

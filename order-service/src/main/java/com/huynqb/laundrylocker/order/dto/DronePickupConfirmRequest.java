package com.huynqb.laundrylocker.order.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/// KTV drone xác nhận đã lấy kiện tại nhà người gửi.
public record DronePickupConfirmRequest(
        /// Mã lấy hàng người gửi đọc cho KTV.
        @NotBlank @Size(max = 6) String pickupCode,
        @AssertTrue(message = "Parcel must match the declaration") boolean parcelMatched,
        @Size(max = 500) String note) {
}

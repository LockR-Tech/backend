package com.huynqb.laundrylocker.order.dto;

import com.huynqb.laundrylocker.common.media.MediaUpload;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/// KTV drone xác nhận đã lấy kiện tại nhà người gửi.
public record DronePickupConfirmRequest(
        /// Mã lấy hàng người gửi đọc cho KTV.
        @NotBlank @Size(max = 6) String pickupCode,
        @AssertTrue(message = "Parcel must match the declaration") boolean parcelMatched,
        @NotNull @Valid MediaUpload pickupPhoto,
        @Size(max = 500) String note) {
}

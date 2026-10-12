package com.huynqb.laundrylocker.order.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/// Xác nhận một đơn drone giao không hoàn tất (KTV drone hoặc admin), hoặc KTV báo không lấy được hàng.
public record DroneDeliveryFailRequest(
        /// Mã trong `DroneFailureReasons`. Bỏ trống thì lấy lý do đơn đang bị chặn.
        @Size(max = 40) String reason,
        /// Mã số cũ (1 thời tiết … 5 khác) của client chưa gửi `reason`.
        @Min(1) @Max(5) Integer reasonCode,
        @Size(max = 500) String note) {
}

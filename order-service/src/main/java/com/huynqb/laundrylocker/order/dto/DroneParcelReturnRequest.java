package com.huynqb.laundrylocker.order.dto;

import jakarta.validation.constraints.Size;

/// Đội bay/admin xác nhận đã trả kiện của một đơn drone không giao được cho người gửi.
public record DroneParcelReturnRequest(@Size(max = 500) String note) {
}

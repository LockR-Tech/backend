package com.huynqb.laundrylocker.order.dto;

import jakarta.validation.constraints.NotBlank;

/// Body đổi trạng thái của admin. `staffId`/`receiveBoxId` cũ (nếu client còn gửi) bị bỏ qua:
/// người thao tác lấy từ JWT, không còn bước chuyển nào cần chọn ô nhận.
public record UpdateOrderStatusRequest(@NotBlank String status) {
}

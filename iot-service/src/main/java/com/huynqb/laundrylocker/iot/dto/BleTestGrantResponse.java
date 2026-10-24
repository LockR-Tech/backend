package com.huynqb.laundrylocker.iot.dto;

import java.time.LocalDateTime;

/// Một tài khoản được thử mở ô qua Bluetooth trên tủ. Tên/email do trang admin tự ghép từ danh sách người dùng.
public record BleTestGrantResponse(Long lockerId, Long userId, Long grantedBy, LocalDateTime createdAt) {
}

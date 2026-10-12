package com.huynqb.laundrylocker.order.dto;

import java.time.LocalDateTime;

/// Đơn drone đang gặp sự cố (chưa kết thúc) hoặc đã kết thúc "giao không hoàn tất": lý do và phiếu sự
/// cố liên quan — mối nối giữa người gửi, KTV sự cố và admin.
public record DroneIncidentLinkView(
        /// Mã trong `DroneFailureReasons`.
        String reason,
        String reasonLabel,
        /// Phiếu sự cố của locker-service; null khi không xác định được phiếu.
        Long ticketId,
        String note,
        /// Lúc bắt đầu bị chặn, hoặc lúc đơn kết thúc.
        LocalDateTime at,
        /// Đơn đang bị chặn sẽ tự kết thúc lúc này nếu chưa ai xử lý; null khi không tự đóng.
        LocalDateTime autoCloseAt) {
}

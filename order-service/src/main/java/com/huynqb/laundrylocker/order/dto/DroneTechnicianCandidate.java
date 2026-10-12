package com.huynqb.laundrylocker.order.dto;

import java.time.LocalDateTime;

/// Một KTV drone trong danh sách admin chọn để gán lấy hàng, gần điểm lấy hàng nhất trước.
public record DroneTechnicianCandidate(
        Long technicianId,
        String name,
        String phone,
        boolean onDuty,
        /// Đang bận việc khác nên chưa gán được.
        boolean busy,
        /// Vị trí còn hiệu lực (app còn gửi vị trí gần đây).
        boolean locationFresh,
        Integer distanceMeters,
        LocalDateTime locationAt) {
}

package com.huynqb.laundrylocker.order.dto;

import java.time.LocalDateTime;

/// Dữ liệu trang "Thời tiết & bay" của admin.
public final class DroneFlightControlViews {

    private DroneFlightControlViews() {
    }

    /// Trạm đang tạm dừng bay.
    public record StationSuspension(
            Long lockerId,
            String reason,
            String reasonLabel,
            String note,
            Long suspendedByUserId,
            LocalDateTime suspendedAt,
            LocalDateTime resumeAt) {
    }

    /// Admin dừng một trạm: lý do (WEATHER, LANDING_PAD, AIRSPACE, OPERATIONAL, OTHER), ghi chú, giờ tự mở lại
    /// (LocalDateTime theo giờ server — UTC — như mọi mốc giờ khác của API).
    public record SuspendStationRequest(String reason, String note, LocalDateTime resumeAt) {
    }
}

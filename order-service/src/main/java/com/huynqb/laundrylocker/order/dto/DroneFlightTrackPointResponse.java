package com.huynqb.laundrylocker.order.dto;

import java.time.LocalDateTime;

/// Một điểm trong nhật ký vị trí bay (`GET /api/admin/drone-orders/{orderId}/flight-track`).
public record DroneFlightTrackPointResponse(
        LocalDateTime recordedAt,
        String stage,
        double latitude,
        double longitude,
        Double altitudeM,
        Double headingDeg,
        Double speedMps,
        Integer batteryPercent,
        String droneCode,
        String source) {
}

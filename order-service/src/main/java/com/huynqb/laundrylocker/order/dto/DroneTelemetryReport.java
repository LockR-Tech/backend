package com.huynqb.laundrylocker.order.dto;

/// Bản sao `DroneTelemetryRequest` của locker-service: số đo drone tự báo để cập nhật đội bay.
public record DroneTelemetryReport(String code, Integer batteryPercent) {
}

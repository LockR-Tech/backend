package com.huynqb.laundrylocker.order.dto;

import java.math.BigDecimal;

/// Bản sao `DroneTelemetryRequest` của locker-service: số đo drone tự báo để cập nhật đội bay.
public record DroneTelemetryReport(
        String code,
        Integer batteryPercent,
        BigDecimal voltageV,
        BigDecimal currentA,
        String macAddress,
        String ipAddress,
        String firmwareVersion,
        String mavlinkStatus,
        String flightMode,
        Boolean armed,
        String landedState,
        Integer gpsFixType,
        Integer gpsSatellites,
        String cameraStatus,
        String cameraStreamUrl) {

    public DroneTelemetryReport(String code, Integer batteryPercent) {
        this(code, batteryPercent, null, null, null, null, null, null, null, null, null, null, null, null, null);
    }
}

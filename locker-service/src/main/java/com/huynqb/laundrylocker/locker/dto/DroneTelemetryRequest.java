package com.huynqb.laundrylocker.locker.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;

/// Số đo drone tự báo qua telemetry (order-service chuyển tiếp), tra theo mã drone hoặc macAddress.
@JsonIgnoreProperties(ignoreUnknown = true)
public record DroneTelemetryRequest(
        @NotBlank String code,
        @Min(0) @Max(100) Integer batteryPercent,
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
}

package com.huynqb.laundrylocker.locker.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record DroneDeviceResponse(
        Long id,
        String macAddress,
        Long droneId,
        String droneCode,
        String ipAddress,
        String firmwareVersion,
        String mavlinkStatus,
        String flightMode,
        Boolean armed,
        String landedState,
        Integer gpsFixType,
        Integer gpsSatellites,
        Integer batteryPercent,
        BigDecimal voltageV,
        BigDecimal currentA,
        String cameraStatus,
        String cameraStreamUrl,
        LocalDateTime lastSeenAt,
        Boolean online,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    /// Cửa sổ phát hiện trực tuyến cho máy tính nhúng drone (companion computer heartbeat):
    /// Pi gửi telemetry mỗi 1-3s; nếu quá 20s không có bản tin thì coi như đã tắt nguồn / mất mạng.
    public static final long ONLINE_WINDOW_SECONDS = 20;

    public static boolean isOnline(LocalDateTime lastSeenAt) {
        return lastSeenAt != null && lastSeenAt.isAfter(LocalDateTime.now().minusSeconds(ONLINE_WINDOW_SECONDS));
    }
}

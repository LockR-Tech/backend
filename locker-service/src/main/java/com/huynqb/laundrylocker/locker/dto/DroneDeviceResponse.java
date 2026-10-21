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

    public static final long ONLINE_WINDOW_SECONDS = 150;

    public static boolean isOnline(LocalDateTime lastSeenAt) {
        return lastSeenAt != null && lastSeenAt.isAfter(LocalDateTime.now().minusSeconds(ONLINE_WINDOW_SECONDS));
    }
}


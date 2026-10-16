package com.huynqb.laundrylocker.locker.dto;

public record TechnicianPresenceRequest(
        Boolean onDuty,
        Double latitude,
        Double longitude,
        Double accuracyM,
        Double headingDeg,
        Double speedMs,
        Double altitudeM,
        Integer batteryLevel,
        String deviceInfo
) {
}


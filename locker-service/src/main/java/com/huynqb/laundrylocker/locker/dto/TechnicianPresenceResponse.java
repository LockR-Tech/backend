package com.huynqb.laundrylocker.locker.dto;

import java.time.LocalDateTime;

public record TechnicianPresenceResponse(
        Long userId,
        String userName,
        String technicianType,
        Boolean onDuty,
        Double latitude,
        Double longitude,
        Double accuracyM,
        Double headingDeg,
        Double speedMs,
        Double altitudeM,
        Integer batteryLevel,
        String deviceInfo,
        LocalDateTime shiftStartedAt,
        LocalDateTime shiftEndedAt,
        LocalDateTime lastLocatedAt,
        LocalDateTime updatedAt
) {
    public static TechnicianPresenceResponse empty(Long userId, String userName, String technicianType) {
        return new TechnicianPresenceResponse(
                userId,
                userName,
                technicianType,
                false,
                null, null, null, null, null, null, null, null, null, null, null, null
        );
    }
}


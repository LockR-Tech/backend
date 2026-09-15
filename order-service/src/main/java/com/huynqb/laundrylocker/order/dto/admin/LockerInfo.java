package com.huynqb.laundrylocker.order.dto.admin;

/// Bản sao LockerResponse của locker-service (/internal/lockers/batch).
public record LockerInfo(
        Long id,
        Long storeId,
        String code,
        String name,
        String status,
        String address,
        Double latitude,
        Double longitude,
        Boolean landingPad,
        String landingMarkerId,
        Integer totalBoxes,
        Integer availableBoxes) {
}
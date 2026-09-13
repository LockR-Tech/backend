package com.huynqb.laundrylocker.locker.dto;

public record LockerResponse(
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

package com.huynqb.laundrylocker.locker.dto;

/// `assignedTechnicianId/Name` chỉ có giá trị ở API admin/KTV; API công khai (app khách, kiosk) để null.
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
        Integer availableBoxes,
        Long assignedTechnicianId,
        String assignedTechnicianName) {

    public LockerResponse(
            Long id, Long storeId, String code, String name, String status, String address,
            Double latitude, Double longitude, Boolean landingPad, String landingMarkerId,
            Integer totalBoxes, Integer availableBoxes) {
        this(id, storeId, code, name, status, address, latitude, longitude, landingPad, landingMarkerId,
                totalBoxes, availableBoxes, null, null);
    }
}

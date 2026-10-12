package com.huynqb.laundrylocker.locker.dto;

/// `assignedTechnicianId/Name` chỉ có giá trị ở API admin/KTV; API công khai (app khách, kiosk) để null.
/// `online`: bộ điều khiển gắn với tủ có tín hiệu trong cửa sổ online — cùng cách tính với `online`
/// của `/api/lockers/{id}/layout`; chưa gắn bộ điều khiển hoặc không tra được iot-service ⇒ false.
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
        String assignedTechnicianName,
        boolean online) {

    public LockerResponse(
            Long id, Long storeId, String code, String name, String status, String address,
            Double latitude, Double longitude, Boolean landingPad, String landingMarkerId,
            Integer totalBoxes, Integer availableBoxes) {
        this(id, storeId, code, name, status, address, latitude, longitude, landingPad, landingMarkerId,
                totalBoxes, availableBoxes, null, null, false);
    }

    public LockerResponse(
            Long id, Long storeId, String code, String name, String status, String address,
            Double latitude, Double longitude, Boolean landingPad, String landingMarkerId,
            Integer totalBoxes, Integer availableBoxes, Long assignedTechnicianId, String assignedTechnicianName) {
        this(id, storeId, code, name, status, address, latitude, longitude, landingPad, landingMarkerId,
                totalBoxes, availableBoxes, assignedTechnicianId, assignedTechnicianName, false);
    }
}

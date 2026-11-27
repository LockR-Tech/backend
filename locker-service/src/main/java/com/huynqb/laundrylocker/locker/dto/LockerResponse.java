package com.huynqb.laundrylocker.locker.dto;

/// `assignedTechnicianId/Name` chỉ có giá trị ở API admin/KTV; API công khai (app khách, kiosk) để null.
/// `online`: bộ điều khiển gắn với tủ có tín hiệu trong cửa sổ online — cùng cách tính với `online`
/// của `/api/lockers/{id}/layout`. `null` = chưa biết (tủ chưa gắn bộ điều khiển, hoặc không tra được iot-service)
/// ⇒ client không coi là mất kết nối; chỉ `false` khi bộ điều khiển đã gắn mà mất tín hiệu.
/// `acceptingOrders`: tủ nhận đơn mới (đang hoạt động VÀ có KTV phụ trách — F3-G16). `orderPauseReason` khi không
/// nhận: `MAINTENANCE` (bảo trì / ngưng), `INSTALLING` (đang lắp đặt), `NO_TECHNICIAN` (chưa có KTV phụ trách).
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
        String landingPadStatus,
        Integer totalBoxes,
        Integer availableBoxes,
        Long assignedTechnicianId,
        String assignedTechnicianName,
        Boolean online,
        Boolean acceptingOrders,
        String orderPauseReason) {

    public LockerResponse(
            Long id, Long storeId, String code, String name, String status, String address,
            Double latitude, Double longitude, Boolean landingPad, String landingMarkerId, String landingPadStatus,
            Integer totalBoxes, Integer availableBoxes, Long assignedTechnicianId, String assignedTechnicianName,
            Boolean online) {
        this(id, storeId, code, name, status, address, latitude, longitude, landingPad, landingMarkerId,
                landingPadStatus, totalBoxes, availableBoxes, assignedTechnicianId, assignedTechnicianName, online,
                null, null);
    }

    public LockerResponse(
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
            Boolean online) {
        this(id, storeId, code, name, status, address, latitude, longitude, landingPad, landingMarkerId,
                null, totalBoxes, availableBoxes, assignedTechnicianId, assignedTechnicianName, online);
    }

    public LockerResponse(
            Long id, Long storeId, String code, String name, String status, String address,
            Double latitude, Double longitude, Boolean landingPad, String landingMarkerId,
            Integer totalBoxes, Integer availableBoxes) {
        this(id, storeId, code, name, status, address, latitude, longitude, landingPad, landingMarkerId,
                null, totalBoxes, availableBoxes, null, null, null);
    }

    public LockerResponse(
            Long id, Long storeId, String code, String name, String status, String address,
            Double latitude, Double longitude, Boolean landingPad, String landingMarkerId,
            Integer totalBoxes, Integer availableBoxes, Long assignedTechnicianId, String assignedTechnicianName) {
        this(id, storeId, code, name, status, address, latitude, longitude, landingPad, landingMarkerId,
                null, totalBoxes, availableBoxes, assignedTechnicianId, assignedTechnicianName, null);
    }
}

package com.huynqb.laundrylocker.order.dto.admin;

/// Bản sao LockerResponse của locker-service (/internal/lockers/batch). `acceptingOrders=false` kèm
/// `orderPauseReason` (MAINTENANCE / INSTALLING / NO_TECHNICIAN): tủ không nhận đơn mới (F3-G16); null = locker-service cũ.
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
        String landingPadStatus,
        Integer totalBoxes,
        Integer availableBoxes,
        Long assignedTechnicianId,
        Boolean acceptingOrders,
        String orderPauseReason) {

    public LockerInfo(
            Long id, Long storeId, String code, String name, String status, String address, Double latitude,
            Double longitude, Boolean landingPad, String landingMarkerId, String landingPadStatus,
            Integer totalBoxes, Integer availableBoxes) {
        this(id, storeId, code, name, status, address, latitude, longitude, landingPad, landingMarkerId,
                landingPadStatus, totalBoxes, availableBoxes, null, null, null);
    }

    /// Tủ chưa có KTV phụ trách (locker-service báo rõ) ⇒ không nhận đơn mới.
    public boolean pausedForNoTechnician() {
        return Boolean.FALSE.equals(acceptingOrders) && "NO_TECHNICIAN".equals(orderPauseReason);
    }

    public LockerInfo(
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
        this(id, storeId, code, name, status, address, latitude, longitude, landingPad, landingMarkerId,
                null, totalBoxes, availableBoxes);
    }
}

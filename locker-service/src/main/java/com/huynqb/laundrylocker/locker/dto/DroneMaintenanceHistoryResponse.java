package com.huynqb.laundrylocker.locker.dto;

import java.util.List;

/** Lịch sử chỉ thuộc về một Drone vật lý, không lẫn lịch sử Kiosk. */
public record DroneMaintenanceHistoryResponse(
        Long droneUnitId,
        String droneCode,
        List<MaintenanceInspectionLogResponse> completedMaintenance,
        List<LockerReportResponse> resolvedIncidents) {
}

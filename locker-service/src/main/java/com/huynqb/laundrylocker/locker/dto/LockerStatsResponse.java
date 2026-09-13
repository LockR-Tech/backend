package com.huynqb.laundrylocker.locker.dto;

public record LockerStatsResponse(
        Long lockerId,
        String code,
        String name,
        String status,
        Boolean landingPad,
        Integer totalCells,
        Integer availableCells,
        Integer reservedCells,
        Integer occupiedCells,
        Integer faultCells,
        Double utilization,
        Long openReports) {
}

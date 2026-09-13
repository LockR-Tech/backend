package com.huynqb.laundrylocker.locker.dto;

import java.time.LocalDateTime;

/// A box whose cabinet-reported door is physically OPEN while the order-driven
/// logical status says it isn't actively OCCUPIED — a likely "door left ajar"
/// anomaly. Surfaced across all lockers for the maintenance shift overview, with
/// locker location so the technician can navigate there (same metadata shape as
/// FaultCellResponse).
public record BoxAnomalyResponse(
        Long lockerId,
        String lockerCode,
        String lockerName,
        String lockerAddress,
        Double lockerLatitude,
        Double lockerLongitude,
        Long boxId,
        Integer boxNumber,
        String cellType,
        String logicalStatus,
        String hwState,
        LocalDateTime lastReportedAt) {
}

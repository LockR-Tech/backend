package com.huynqb.laundrylocker.locker.dto;

import java.time.LocalDateTime;

/// Maintenance box-health view: the order-driven logical status (owned by
/// locker-service) side-by-side with the cabinet-reported hardware door state
/// (owned by iot-service, GAP 2). `doorOpen` = hardware says the door is OPEN;
/// `needsAttention` = door open while the box isn't actively OCCUPIED — a likely
/// "door left ajar" anomaly worth a maintenance look. `hwState`/`lastReportedAt`
/// are null when the cabinet has never reported that box.
public record BoxHealthResponse(
        Long boxId,
        Integer boxNumber,
        String cellType,
        String logicalStatus,
        String hwState,
        LocalDateTime lastReportedAt,
        boolean doorOpen,
        boolean needsAttention) {
}

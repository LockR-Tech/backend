package com.huynqb.laundrylocker.iot.dto;

import java.time.LocalDateTime;

/// GAP 2: physical/hardware box state reported by the cabinet, exposed read-only
/// to Manager/Admin via `GET /api/manage/iot/box-status` so ops can compare it
/// against the order-driven logical status owned by locker-service.
public record BoxHardwareStatusResponse(
        Long boxId, Long lockerId, String hwState, LocalDateTime lastReportedAt) {
}

package com.huynqb.laundrylocker.order.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/// Phiếu sự cố còn mở của locker-service (`/internal/reports/open`), chỉ các trường order-service cần
/// để biết phiếu nào đang chặn một đơn drone.
@JsonIgnoreProperties(ignoreUnknown = true)
public record OpenReportDto(
        Long id,
        Long lockerId,
        Long boxId,
        Long droneUnitId,
        String title,
        String status,
        /// BOX | DRONE | LANDING_PAD | LOCKER
        String category,
        Boolean blocksLocker) {
}

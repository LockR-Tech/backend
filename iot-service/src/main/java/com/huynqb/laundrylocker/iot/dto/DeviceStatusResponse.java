package com.huynqb.laundrylocker.iot.dto;

import java.time.LocalDateTime;

/// `status` là trạng thái đang áp dụng: ONLINE chỉ khi `lastSeenAt` nằm trong cửa sổ online
/// (cùng cửa sổ với bộ điều khiển tủ); thiết bị báo ONLINE nhưng mất tín hiệu ⇒ OFFLINE.
/// `lockerCode`/`lockerName` tra từ locker-service, null khi không tra được.
public record DeviceStatusResponse(
        Long id,
        String deviceId,
        Long lockerId,
        String status,
        LocalDateTime lastSeenAt,
        String lockerCode,
        String lockerName,
        boolean online) {
}

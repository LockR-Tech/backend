package com.huynqb.laundrylocker.iot.dto;

import jakarta.validation.constraints.NotNull;

/// Gán bộ điều khiển vào tủ. `testDoors` (mặc định true): Pi mở lần lượt từng ô để thử;
/// false = chỉ gửi sơ đồ ô, không mở ô nào (dùng khi tủ đang có hàng).
public record AssignGatewayRequest(@NotNull Long lockerId, Boolean testDoors) {
}

package com.huynqb.laundrylocker.order.dto;

import java.time.LocalDateTime;

/// `latitude`/`longitude`/`locationSource`: vị trí kiện lúc ghi mốc (chỉ đơn drone), xem `OrderHistoryLocator`.
public record OrderTimelineEvent(String oldStatus, String newStatus, Long changedByUserId, String note,
                                 LocalDateTime createdAt, Double latitude, Double longitude, String locationSource) {
}

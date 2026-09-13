package com.huynqb.laundrylocker.order.dto;

import java.time.LocalDateTime;

public record OrderTimelineEvent(String oldStatus, String newStatus, Long changedByUserId, String note,
                                 LocalDateTime createdAt) {
}

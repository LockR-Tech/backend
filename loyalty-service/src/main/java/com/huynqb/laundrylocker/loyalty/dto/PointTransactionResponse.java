package com.huynqb.laundrylocker.loyalty.dto;

import java.time.LocalDateTime;

public record PointTransactionResponse(Long id, Long userId, Long orderId, Integer points, String type,
                                       LocalDateTime createdAt) {
}

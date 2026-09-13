package com.huynqb.laundrylocker.order.dto;

import java.time.LocalDateTime;

public record OrderRatingResponse(Long id, Long orderId, Long userId, Integer rating, String comment,
                                  LocalDateTime createdAt) {
}

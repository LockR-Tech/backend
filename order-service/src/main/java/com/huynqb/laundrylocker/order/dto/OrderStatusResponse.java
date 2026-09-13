package com.huynqb.laundrylocker.order.dto;

import java.time.LocalDateTime;

public record OrderStatusResponse(
        Long orderId,
        String status,
        String statusDescription,
        String pinCode,
        Long lockerId,
        Long boxId,
        LocalDateTime estimatedReadyAt,
        Boolean isPaid,
        String nextAction) {
}

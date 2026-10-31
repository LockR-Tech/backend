package com.huynqb.laundrylocker.loyalty.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/// Một giao dịch điểm trên trang /admin/loyalty. `orderCode`/`orderType` tra từ order-service, null khi
/// giao dịch không gắn đơn hoặc order-service không trả lời. `reference` là mã lần đổi voucher.
public record AdminPointTransactionResponse(
        Long id,
        Long userId,
        Long orderId,
        String orderCode,
        String orderType,
        Integer points,
        String type,
        String reference,
        BigDecimal amount,
        LocalDateTime createdAt) {
}

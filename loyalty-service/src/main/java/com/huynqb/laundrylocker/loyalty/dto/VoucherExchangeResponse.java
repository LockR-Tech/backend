package com.huynqb.laundrylocker.loyalty.dto;

/// Kết quả đổi voucher. `exchanged = false` khi không đủ điểm (không trừ gì, `balance` là số dư hiện có) —
/// trả trong body thay vì lỗi HTTP để order-service báo đúng câu cho khách.
public record VoucherExchangeResponse(boolean exchanged, int pointsSpent, int balance) {
}

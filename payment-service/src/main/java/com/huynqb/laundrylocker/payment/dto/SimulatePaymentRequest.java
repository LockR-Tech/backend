package com.huynqb.laundrylocker.payment.dto;

import java.math.BigDecimal;

/// Giả lập đã nhận chuyển khoản cho một giao dịch đang chờ: `paymentId` (thanh toán đơn) hoặc
/// `referenceId` (mã nạp ví `TOPUP_…`); `amount` bỏ trống = đủ số tiền của giao dịch.
public record SimulatePaymentRequest(Long paymentId, String referenceId, BigDecimal amount) {
}

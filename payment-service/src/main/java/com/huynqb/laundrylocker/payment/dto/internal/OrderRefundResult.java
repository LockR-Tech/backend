package com.huynqb.laundrylocker.payment.dto.internal;

import java.math.BigDecimal;

/// Kết quả hoàn tiền một đơn: tổng đã cộng vào ví ở lần gọi này và số giao dịch được hoàn.
/// `refundedAmount = 0` nghĩa là đơn không có khoản nào cần hoàn (chưa trả, hoặc đã hoàn rồi).
public record OrderRefundResult(Long orderId, BigDecimal refundedAmount, int refundedPayments) {
}

package com.huynqb.laundrylocker.payment.dto.admin;

import java.util.List;

/// Chi tiết giao dịch: các lần hoàn tiền của giao dịch và các giao dịch khác của cùng đơn.
public record AdminPaymentDetailResponse(
        AdminPaymentResponse payment,
        List<AdminRefundResponse> refunds,
        List<AdminPaymentResponse> orderPayments) {
}
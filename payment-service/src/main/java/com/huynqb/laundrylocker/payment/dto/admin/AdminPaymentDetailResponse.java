package com.huynqb.laundrylocker.payment.dto.admin;

import java.util.List;

/// Chi tiết giao dịch: refunds của payment, bút toán ví liên quan (nạp ví hoặc trừ ví
/// khi thanh toán đơn) và các giao dịch khác của cùng đơn.
public record AdminPaymentDetailResponse(
        AdminPaymentResponse payment,
        List<AdminRefundResponse> refunds,
        List<AdminWalletTransactionResponse> walletTransactions,
        List<AdminPaymentResponse> orderPayments) {
}
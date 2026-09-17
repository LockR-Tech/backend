package com.huynqb.laundrylocker.payment.service;

import com.huynqb.laundrylocker.payment.model.WalletTransaction;

/// Suy ra đơn hàng gốc của một biến động ví từ `referenceId` (định dạng
/// `ORDERPAY-{orderId}`, xem {@link WalletService#debit}/callers).
///
/// Dùng chung cho cả API admin ({@code AdminWalletTransactionResponse}) và API khách hàng
/// ({@code WalletTransactionResponse}) — trước đây mỗi nơi tự suy luận riêng (hoặc không
/// suy luận), khiến "mã đơn hàng" hiển thị lệch nhau giữa web admin và app mobile cho cùng
/// một biến động ví. Gom về một chỗ để hai bên luôn ra cùng một orderId.
final class WalletTransactionRefs {

    static final String ORDER_PAYMENT_REF_PREFIX = "ORDERPAY-";

    private WalletTransactionRefs() {
    }

    /// `null` khi biến động không phải thanh toán đơn (nạp ví, admin điều chỉnh…) hoặc
    /// `referenceId` không đúng định dạng.
    static Long relatedOrderId(WalletTransaction tx) {
        if (!WalletService.SOURCE_ORDER_PAYMENT.equalsIgnoreCase(tx.getSource())
                || tx.getReferenceId() == null
                || !tx.getReferenceId().startsWith(ORDER_PAYMENT_REF_PREFIX)) {
            return null;
        }
        try {
            return Long.parseLong(tx.getReferenceId().substring(ORDER_PAYMENT_REF_PREFIX.length()));
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}

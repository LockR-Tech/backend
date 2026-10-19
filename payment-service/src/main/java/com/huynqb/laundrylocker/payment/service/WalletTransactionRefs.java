package com.huynqb.laundrylocker.payment.service;

import com.huynqb.laundrylocker.payment.model.WalletTransaction;

/// Suy ra đơn hàng gốc của một biến động ví từ `referenceId` (định dạng
/// `ORDERPAY-{orderId}` cho lần trả đầu, `ORDERPAY-{orderId}-{n}` cho lần trả thứ n ≥ 2,
/// xem {@link WalletService#debit}/callers).
///
/// Dùng chung cho cả API admin ({@code AdminWalletTransactionResponse}) và API khách hàng
/// ({@code WalletTransactionResponse}) — trước đây mỗi nơi tự suy luận riêng (hoặc không
/// suy luận), khiến "mã đơn hàng" hiển thị lệch nhau giữa web admin và app mobile cho cùng
/// một biến động ví. Gom về một chỗ để hai bên luôn ra cùng một orderId.
final class WalletTransactionRefs {

    static final String ORDER_PAYMENT_REF_PREFIX = "ORDERPAY-";

    private WalletTransactionRefs() {
    }

    /// Mã chống trùng của lần trả thứ `sequence` (bắt đầu từ 1) cho đơn. Lần đầu giữ dạng cũ
    /// `ORDERPAY-{orderId}` để các biến động đã ghi trước đây vẫn khớp.
    static String orderPaymentRef(Long orderId, long sequence) {
        return sequence <= 1
                ? ORDER_PAYMENT_REF_PREFIX + orderId
                : ORDER_PAYMENT_REF_PREFIX + orderId + "-" + sequence;
    }

    /// `null` khi biến động không phải thanh toán đơn (nạp ví, admin điều chỉnh…) hoặc
    /// `referenceId` không đúng định dạng.
    static Long relatedOrderId(WalletTransaction tx) {
        if (!WalletService.SOURCE_ORDER_PAYMENT.equalsIgnoreCase(tx.getSource())
                || tx.getReferenceId() == null
                || !tx.getReferenceId().startsWith(ORDER_PAYMENT_REF_PREFIX)) {
            return null;
        }
        String rest = tx.getReferenceId().substring(ORDER_PAYMENT_REF_PREFIX.length());
        int dash = rest.indexOf('-');
        try {
            return Long.parseLong(dash < 0 ? rest : rest.substring(0, dash));
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}

package com.huynqb.laundrylocker.payment.service;

import com.huynqb.laundrylocker.payment.model.WalletTransaction;

/// Suy ra đơn hàng gốc của một dòng `wallet_transactions` cũ từ `referenceId` (định dạng
/// `ORDERPAY-{orderId}` cho lần trả đầu, `ORDERPAY-{orderId}-{n}` cho lần trả thứ n ≥ 2). Ví đã gỡ khỏi sản
/// phẩm; chỉ còn dùng để lịch sử giao dịch của khách hiện đúng mã đơn cho các dòng cũ.
final class WalletTransactionRefs {

    static final String ORDER_PAYMENT_REF_PREFIX = "ORDERPAY-";
    static final String SOURCE_ORDER_PAYMENT = "ORDER_PAYMENT";

    private WalletTransactionRefs() {
    }

    /// `null` khi dòng không phải thanh toán đơn (nạp, điều chỉnh…) hoặc `referenceId` không đúng định dạng.
    static Long relatedOrderId(WalletTransaction tx) {
        if (!SOURCE_ORDER_PAYMENT.equalsIgnoreCase(tx.getSource())
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

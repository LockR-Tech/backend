package com.huynqb.laundrylocker.payment.service;

import com.huynqb.laundrylocker.payment.model.PaymentRecord;
import com.huynqb.laundrylocker.payment.model.RefundRecord;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Set;

/**
 * Quy tắc dùng chung cho báo cáo thanh toán/doanh thu.
 *
 * <ul>
 *   <li>Nạp ví (method {@code *_TOPUP}, orderId = 0) là tiền gửi, không phải doanh thu.</li>
 *   <li>Tiền đã thu của một đơn = payment COMPLETED, orderId &gt; 0, không phải nạp ví
 *       (gồm cả WALLET — trừ ví khi thanh toán đơn).</li>
 *   <li>Bảng payments không có cột paid_at: thời điểm thu = updatedAt của payment COMPLETED
 *       (CASH/WALLET hoàn tất ngay lúc tạo; SEPAY cập nhật khi webhook về).</li>
 *   <li>Hoàn tiền trừ doanh thu = refund COMPLETED có orderId &gt; 0, tính theo processedAt.</li>
 * </ul>
 */
public final class PaymentReportRules {

    public static final String STATUS_COMPLETED = "COMPLETED";
    /// Method của giao dịch nạp ví. VNPAY_TOPUP chỉ còn trong dữ liệu cũ (VNPay đã ngừng hỗ trợ) —
    /// giữ để báo cáo và bộ lọc lịch sử vẫn xếp đúng loại.
    public static final Set<String> TOPUP_METHODS = Set.of("SEPAY_TOPUP", "VNPAY_TOPUP");
    public static final String KIND_ORDER = "ORDER";
    public static final String KIND_TOPUP = "TOPUP";

    private PaymentReportRules() {
    }

    public static boolean isTopupMethod(String method) {
        return method != null && TOPUP_METHODS.contains(method.toUpperCase(Locale.ROOT));
    }

    public static boolean isTopup(PaymentRecord payment) {
        return isTopupMethod(payment.getMethod())
                || payment.getOrderId() == null
                || payment.getOrderId() <= 0;
    }

    public static String kind(PaymentRecord payment) {
        return isTopup(payment) ? KIND_TOPUP : KIND_ORDER;
    }

    public static boolean isCompleted(PaymentRecord payment) {
        return STATUS_COMPLETED.equalsIgnoreCase(payment.getStatus());
    }

    public static boolean isCollectedOrderPayment(PaymentRecord payment) {
        return isCompleted(payment) && !isTopup(payment);
    }

    public static LocalDateTime paidAt(PaymentRecord payment) {
        if (!isCompleted(payment)) {
            return null;
        }
        return payment.getUpdatedAt() != null ? payment.getUpdatedAt() : payment.getCreatedAt();
    }

    public static boolean isCompletedOrderRefund(RefundRecord refund) {
        return STATUS_COMPLETED.equalsIgnoreCase(refund.getStatus())
                && refund.getOrderId() != null
                && refund.getOrderId() > 0;
    }

    public static LocalDateTime refundedAt(RefundRecord refund) {
        return refund.getProcessedAt() != null ? refund.getProcessedAt() : refund.getRequestedAt();
    }

    public static BigDecimal amount(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}

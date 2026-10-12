package com.huynqb.laundrylocker.payment.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/// createdAt/updatedAt/content thêm sau (2026-09) — chỉ bổ sung, client cũ bỏ qua được.
/// Thời gian là LocalDateTime theo múi giờ JVM (UTC trên container).
/// `bankTransfer` (2026-10): chỉ có với thanh toán SePay khi đã cấu hình tài khoản nhận — app dùng để
/// mở app ngân hàng điền sẵn (VietQR deeplink) thay vì tự đọc lại URL ảnh QR.
public record PaymentResponse(
        Long id,
        Long orderId,
        Long userId,
        BigDecimal amount,
        String method,
        String status,
        String referenceId,
        String referenceTransactionId,
        String paymentUrl,
        String qrCodeUrl,
        String deeplink,
        String description,
        String content,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        BankTransfer bankTransfer) {

    /// Thông tin chuyển khoản tới tài khoản nhận của SePay. `transferContent` phải giữ nguyên để webhook
    /// SePay khớp được giao dịch với đơn.
    public record BankTransfer(
            String bankBin,
            String accountNo,
            String accountName,
            BigDecimal amount,
            String transferContent) {
    }
}

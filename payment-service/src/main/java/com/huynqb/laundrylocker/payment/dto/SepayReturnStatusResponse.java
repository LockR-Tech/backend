package com.huynqb.laundrylocker.payment.dto;

import java.math.BigDecimal;

/// Trang quay về sau cổng SePay chỉ báo trạng thái hiện tại của giao dịch (không ghi nhận tiền).
/// `paid` = đã được webhook SePay xác nhận. Không kèm userId/orderId vì URL này công khai.
public record SepayReturnStatusResponse(
        String referenceId,
        String status,
        boolean paid,
        String method,
        BigDecimal amount) {
}

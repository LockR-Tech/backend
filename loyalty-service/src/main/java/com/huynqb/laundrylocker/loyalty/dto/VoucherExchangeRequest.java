package com.huynqb.laundrylocker.loyalty.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/// order-service xin trừ `points` điểm để khách đổi một voucher (`POST /internal/loyalty/voucher-exchanges`).
/// `reference` là mã của lần đổi này: gọi lại cùng mã trả đúng kết quả lần đầu, không trừ thêm.
public record VoucherExchangeRequest(
        @NotNull Long userId,
        @NotNull @Min(1) Integer points,
        @NotBlank @Size(max = 64) String reference) {
}

package com.huynqb.laundrylocker.order.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record SendOrderRequest(
        @NotNull Long lockerId,
        Long boxId,
        String size,
        @NotBlank String receiverPhone,
        String receiverName,
        /// Tuỳ chọn. Có thì mã mở tủ gửi được cho người nhận CHƯA có tài khoản Lock.R.
        @Email(message = "receiverEmail không hợp lệ") String receiverEmail,
        String note,
        BigDecimal totalPrice,
        String promotionCode) {
}

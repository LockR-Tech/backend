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
        /// Bắt buộc (kiểm ở OrderService.createSend, lỗi RECEIVER_EMAIL_REQUIRED): mã mở tủ luôn gửi được cho
        /// người nhận, kể cả chưa có tài khoản Lock.R, và người gửi gửi lại được từ chi tiết đơn.
        @Email(message = "receiverEmail không hợp lệ") String receiverEmail,
        String note,
        BigDecimal totalPrice,
        String promotionCode,
        /// Số điểm thưởng muốn dùng (tuỳ chọn); loyalty-service kẹp theo quy tắc admin.
        Integer pointsToUse) {
}

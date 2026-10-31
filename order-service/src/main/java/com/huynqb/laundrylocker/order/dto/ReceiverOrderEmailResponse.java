package com.huynqb.laundrylocker.order.dto;

import java.time.LocalDateTime;

/// Kết quả "Gửi lại qua email" của người nhận: email đã che bớt và email có kèm mã mở tủ không.
public record ReceiverOrderEmailResponse(String maskedEmail, boolean includesPickupCode, LocalDateTime sentAt) {
}

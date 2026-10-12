package com.huynqb.laundrylocker.locker.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.Size;

/// Body gia hạn SLA: `{hours, reason}` (app KTV) — web admin cũ gửi `extensionHours`, vẫn nhận.
/// Số giờ, lý do và giới hạn số lần kiểm trong service để trả đúng mã lỗi (SLA_EXTENSION_*).
public record ExtendSlaRequest(
        @JsonAlias("extensionHours")
        Integer hours,

        @Size(max = 1000)
        String reason) {

    /// Tên cũ, giữ cho mã gọi hiện có.
    public Integer extensionHours() {
        return hours;
    }
}

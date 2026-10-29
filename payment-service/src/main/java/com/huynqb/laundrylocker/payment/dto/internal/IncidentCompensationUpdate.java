package com.huynqb.laundrylocker.payment.dto.internal;

/// `status` = PAID | PAYMENT_FAILED; `reference` = mã phiếu bồi thường (RF-CMP-…).
public record IncidentCompensationUpdate(String status, String reference, String detail) {
}

package com.huynqb.laundrylocker.auth.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.Set;

/// Request/response của đăng nhập kiosk bằng QR (CONTRACTS §1).
public final class KioskQrDtos {

    private KioskQrDtos() {
    }

    /// Kiosk tạo phiên: `{ "lockerId": 7 }`.
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CreateRequest(Long lockerId) {
    }

    /// App khách xác nhận: `{ "confirmCode": "..." }`.
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ConfirmRequest(String confirmCode) {
    }

    /// `pollToken` chỉ kiosk giữ (không đưa lên QR); `qrContent` chỉ chứa sessionId + confirmCode.
    public record SessionCreated(
            String sessionId,
            String pollToken,
            String confirmCode,
            String qrContent,
            Long lockerId,
            Instant expiresAt,
            long pollIntervalMs) {
    }

    /// Kết quả poll. Token chỉ có ở đúng một lần poll đầu tiên sau khi khách xác nhận (status CONFIRMED);
    /// các lần sau status = CONSUMED, không còn token.
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record SessionStatus(
            String status,
            Instant expiresAt,
            KioskUser user,
            String accessToken,
            String refreshToken,
            String tokenType,
            Set<String> roles) {

        public static SessionStatus of(String status, Instant expiresAt) {
            return new SessionStatus(status, expiresAt, null, null, null, null, null);
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record KioskUser(Long id, String fullName, String phoneNumber) {
    }

    public record ConfirmResult(Long lockerId, String lockerName, boolean confirmed) {
    }
}

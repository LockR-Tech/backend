package com.huynqb.laundrylocker.auth.controller;

import com.huynqb.laundrylocker.auth.dto.KioskQrDtos.ConfirmRequest;
import com.huynqb.laundrylocker.auth.dto.KioskQrDtos.ConfirmResult;
import com.huynqb.laundrylocker.auth.dto.KioskQrDtos.CreateRequest;
import com.huynqb.laundrylocker.auth.dto.KioskQrDtos.SessionCreated;
import com.huynqb.laundrylocker.auth.dto.KioskQrDtos.SessionStatus;
import com.huynqb.laundrylocker.auth.service.KioskQrLoginService;
import com.huynqb.laundrylocker.common.dto.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

/// Đăng nhập kiosk bằng QR (CONTRACTS §1). Cả nhóm nằm dưới /api/auth (gateway để công khai): tạo/poll/huỷ
/// là của kiosk chưa đăng nhập (xác thực bằng pollToken), xác nhận là của app khách — gateway chỉ gắn
/// `X-User-Id` khi có access token hợp lệ, thiếu header thì service trả AUTH_REQUIRED.
@RestController
@RequiredArgsConstructor
public class KioskQrLoginController {

    static final String POLL_TOKEN_HEADER = "X-Kiosk-Poll-Token";

    private final KioskQrLoginService kioskQrLoginService;

    @PostMapping("/api/auth/kiosk/qr-sessions")
    public ApiResponse<SessionCreated> create(
            @RequestBody(required = false) CreateRequest request, HttpServletRequest http) {
        return ApiResponse.ok(
                "KIOSK_QR_CREATED", "Đã tạo mã đăng nhập kiosk",
                kioskQrLoginService.create(request == null ? null : request.lockerId(), clientIp(http)));
    }

    @GetMapping("/api/auth/kiosk/qr-sessions/{sessionId}")
    public ApiResponse<SessionStatus> poll(
            @PathVariable String sessionId,
            @RequestHeader(value = POLL_TOKEN_HEADER, required = false) String pollToken) {
        return ApiResponse.ok(
                "KIOSK_QR_STATUS", "Trạng thái phiên đăng nhập kiosk", kioskQrLoginService.poll(sessionId, pollToken));
    }

    @PostMapping("/api/auth/kiosk/qr-sessions/{sessionId}/confirm")
    public ApiResponse<ConfirmResult> confirm(
            @PathVariable String sessionId,
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @RequestBody(required = false) ConfirmRequest request) {
        return ApiResponse.ok(
                "KIOSK_QR_CONFIRMED", "Đã đăng nhập trên kiosk",
                kioskQrLoginService.confirm(sessionId, userId, request == null ? null : request.confirmCode()));
    }

    @PostMapping("/api/auth/kiosk/qr-sessions/{sessionId}/cancel")
    public ApiResponse<SessionStatus> cancel(
            @PathVariable String sessionId,
            @RequestHeader(value = POLL_TOKEN_HEADER, required = false) String pollToken) {
        return ApiResponse.ok(
                "KIOSK_QR_CANCELLED", "Đã huỷ phiên đăng nhập kiosk", kioskQrLoginService.cancel(sessionId, pollToken));
    }

    /// nginx đặt `X-Real-IP` = địa chỉ máy khách (ghi đè giá trị client tự gửi); chạy thẳng không qua nginx
    /// thì dùng địa chỉ kết nối.
    static String clientIp(HttpServletRequest http) {
        String realIp = http.getHeader("X-Real-IP");
        return StringUtils.hasText(realIp) ? realIp.trim() : http.getRemoteAddr();
    }
}

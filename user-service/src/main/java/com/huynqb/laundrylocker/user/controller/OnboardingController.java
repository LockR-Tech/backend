package com.huynqb.laundrylocker.user.controller;

import com.huynqb.laundrylocker.common.dto.ApiResponse;
import com.huynqb.laundrylocker.user.dto.OnboardingAutoShowRequest;
import com.huynqb.laundrylocker.user.dto.OnboardingSeenRequest;
import com.huynqb.laundrylocker.user.dto.OnboardingStateView;
import com.huynqb.laundrylocker.user.service.OnboardingService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/// Hướng dẫn lần đầu trên màn hình (F3-G13), dùng chung cho web admin và app mobile. Đường `/api/user/**` đi qua
/// gateway với mọi JWT hợp lệ; người dùng chỉ đọc/ghi được trạng thái của CHÍNH MÌNH vì id lấy từ `X-User-Id`
/// (gateway ghi đè theo JWT), body không có trường id nào được dùng.
@RestController
@RequiredArgsConstructor
public class OnboardingController {

    private final OnboardingService onboardingService;

    @GetMapping("/api/user/onboarding")
    public ApiResponse<OnboardingStateView> state(@RequestHeader("X-User-Id") Long userId) {
        return ApiResponse.ok("ONBOARDING_STATE_OK", "Onboarding state", onboardingService.get(userId));
    }

    /// Body `{autoShow}`: false = "Bỏ qua vĩnh viễn".
    @PutMapping("/api/user/onboarding/auto-show")
    public ApiResponse<OnboardingStateView> autoShow(
            @RequestHeader("X-User-Id") Long userId, @RequestBody(required = false) OnboardingAutoShowRequest request) {
        return ApiResponse.ok("ONBOARDING_AUTO_SHOW_UPDATED", "Đã lưu cài đặt hướng dẫn",
                onboardingService.setAutoShow(userId, request == null ? null : request.autoShow()));
    }

    /// Body `{group, version}`: đã xem xong một nhóm hướng dẫn.
    @PostMapping("/api/user/onboarding/seen")
    public ApiResponse<OnboardingStateView> seen(
            @RequestHeader("X-User-Id") Long userId, @RequestBody(required = false) OnboardingSeenRequest request) {
        return ApiResponse.ok("ONBOARDING_GROUP_SEEN", "Đã ghi nhận hướng dẫn đã xem",
                onboardingService.markSeen(userId, request == null ? null : request.group(),
                        request == null ? null : request.version()));
    }
}

package com.huynqb.laundrylocker.loyalty.controller;

import com.huynqb.laundrylocker.common.dto.ApiResponse;
import com.huynqb.laundrylocker.common.exception.BusinessException;
import com.huynqb.laundrylocker.common.security.UserRoles;
import com.huynqb.laundrylocker.loyalty.dto.*;
import com.huynqb.laundrylocker.loyalty.service.LoyaltyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Objects;

@RestController
@RequiredArgsConstructor
public class LoyaltyController {

    private final LoyaltyService loyaltyService;

    // /api/loyalty/** đi qua gateway với mọi JWT hợp lệ nên quyền kiểm ở đây: người dùng lấy từ X-User-Id
    // (gateway gắn), không từ body/path; thao tác theo userId tuỳ ý chỉ ADMIN.

    /// Chính chủ hoặc ADMIN.
    @GetMapping("/api/loyalty/users/{userId}")
    public ApiResponse<LoyaltyAccountResponse> getByUser(
            @PathVariable Long userId,
            @RequestHeader(value = "X-User-Id", required = false) Long callerId,
            @RequestHeader(value = "X-User-Roles", required = false) String roles) {
        if (!Objects.equals(userId, callerId)) {
            requireAdmin(roles);
        }
        return ApiResponse.ok(loyaltyService.getByUser(userId));
    }

    private static Long requireCaller(Long callerId) {
        if (callerId == null) {
            throw new BusinessException("AUTH_REQUIRED", "Vui lòng đăng nhập", HttpStatus.UNAUTHORIZED);
        }
        return callerId;
    }

    private static void requireAdmin(String roles) {
        if (!UserRoles.isAdmin(roles)) {
            throw new BusinessException(
                    "LOYALTY_FORBIDDEN", "Không có quyền thực hiện thao tác này", HttpStatus.FORBIDDEN);
        }
    }

    @GetMapping("/api/loyalty/summary")
    public ApiResponse<LoyaltyAccountResponse> summary(@org.springframework.web.bind.annotation.RequestHeader("X-User-Id") Long userId) {
        return ApiResponse.ok(loyaltyService.getByUser(userId));
    }

    @GetMapping("/api/loyalty/points")
    public ApiResponse<LoyaltyAccountResponse> points(@org.springframework.web.bind.annotation.RequestHeader("X-User-Id") Long userId) {
        return summary(userId);
    }

    /// Xem trước dùng điểm cho đơn `orderTotal` đồng (số tiền SAU mã giảm giá) của chính người gọi.
    @GetMapping("/api/loyalty/redeem-preview")
    public ApiResponse<RedeemPreviewResponse> redeemPreview(
            @RequestHeader(value = "X-User-Id", required = false) Long callerId,
            @RequestParam(value = "orderTotal", required = false) java.math.BigDecimal orderTotal) {
        return ApiResponse.ok(loyaltyService.redeemPreview(requireCaller(callerId), orderTotal));
    }

    @GetMapping("/api/loyalty/points/history")
    public ApiResponse<List<PointTransactionResponse>> history(@org.springframework.web.bind.annotation.RequestHeader("X-User-Id") Long userId) {
        return ApiResponse.ok(loyaltyService.history(userId));
    }

    /// Body `{points, reason?}` — đổi điểm của chính người gọi (userId trong body bị bỏ qua).
    @PostMapping("/api/loyalty/redeem-points")
    public ApiResponse<LoyaltyAccountResponse> redeemPoints(
            @RequestHeader(value = "X-User-Id", required = false) Long callerId,
            @Valid @RequestBody RedeemPointsRequest request) {
        Long userId = requireCaller(callerId);
        return ApiResponse.ok("POINTS_REDEEMED", "Points redeemed",
                loyaltyService.redeemPoints(new RedeemPointsRequest(userId, request.points(), request.reason())));
    }

    /// Body `{stamps, rewardName?}` — đổi tem của chính người gọi (userId trong body bị bỏ qua).
    @PostMapping("/api/loyalty/redeem-stamp")
    public ApiResponse<LoyaltyAccountResponse> redeemStamp(
            @RequestHeader(value = "X-User-Id", required = false) Long callerId,
            @Valid @RequestBody RedeemStampRequest request) {
        Long userId = requireCaller(callerId);
        return ApiResponse.ok("STAMP_REDEEMED", "Stamp redeemed",
                loyaltyService.redeemStamp(new RedeemStampRequest(userId, request.stamps(), request.rewardName())));
    }

    @GetMapping("/api/loyalty/stamps")
    public ApiResponse<List<Map<String, Object>>> stamps(@org.springframework.web.bind.annotation.RequestHeader("X-User-Id") Long userId) {
        return ApiResponse.ok(loyaltyService.stampCards(userId));
    }

    @GetMapping("/api/loyalty/stamps/{stampCardId}")
    public ApiResponse<Map<String, Object>> stamp(
            @PathVariable Long stampCardId,
            @org.springframework.web.bind.annotation.RequestHeader("X-User-Id") Long userId) {
        return ApiResponse.ok(loyaltyService.stampCard(userId, stampCardId));
    }

    @GetMapping("/api/loyalty/rewards")
    public ApiResponse<List<Map<String, Object>>> rewards() {
        return ApiResponse.ok(loyaltyService.rewards());
    }

    @PostMapping("/api/loyalty/rewards/{rewardId}/redeem")
    public ApiResponse<LoyaltyAccountResponse> redeemReward(
            @PathVariable Long rewardId,
            @org.springframework.web.bind.annotation.RequestHeader("X-User-Id") Long userId) {
        return ApiResponse.ok("REWARD_REDEEMED", "Reward redeemed", loyaltyService.redeemReward(userId, rewardId));
    }

    @GetMapping("/api/loyalty/points/expiring")
    public ApiResponse<List<Map<String, Object>>> expiringPoints(@org.springframework.web.bind.annotation.RequestHeader("X-User-Id") Long userId) {
        return ApiResponse.ok(loyaltyService.expiringPoints(userId));
    }

    /// Điều chỉnh điểm của một userId bất kỳ (body `{userId, points, type?, orderId?}`) — chỉ ADMIN.
    /// Điểm mua hàng được cộng tự động khi đơn hoàn tất, app khách không gọi endpoint này.
    @PostMapping("/api/loyalty/points")
    public ApiResponse<LoyaltyAccountResponse> adjustPoints(
            @RequestHeader(value = "X-User-Roles", required = false) String roles,
            @Valid @RequestBody AdjustPointsRequest request) {
        requireAdmin(roles);
        return ApiResponse.ok("LOYALTY_POINTS_ADJUSTED", "Loyalty points adjusted", loyaltyService.adjustPoints(request));
    }

    @PostMapping("/api/admin/loyalty/users/{userId}/points")
    public ApiResponse<LoyaltyAccountResponse> adminAdjust(
            @PathVariable Long userId,
            @Valid @RequestBody AdjustPointsRequest request) {
        return ApiResponse.ok("LOYALTY_POINTS_ADJUSTED", "Loyalty points adjusted",
                loyaltyService.adjustPoints(new AdjustPointsRequest(userId, request.orderId(), request.points(), request.type())));
    }

    @GetMapping("/api/admin/loyalty/users/{userId}/history")
    public ApiResponse<List<PointTransactionResponse>> adminHistory(@PathVariable Long userId) {
        return ApiResponse.ok(loyaltyService.history(userId));
    }

    @GetMapping("/api/admin/loyalty/users/{userId}")
    public ApiResponse<LoyaltyAccountResponse> adminByUser(@PathVariable Long userId) {
        return ApiResponse.ok(loyaltyService.getByUser(userId));
    }

    @GetMapping("/api/admin/loyalty")
    public ApiResponse<LoyaltyAccountResponse> adminByUserQuery(@org.springframework.web.bind.annotation.RequestParam Long userId) {
        return adminByUser(userId);
    }

    @GetMapping("/api/admin/loyalty/statistics")
    public ApiResponse<LoyaltyStatisticsResponse> adminStatistics() {
        return ApiResponse.ok(loyaltyService.statistics());
    }

    /// order-service trừ điểm cho đơn vừa tạo (chỉ trong cụm — gateway chặn mọi đường /internal).
    @PostMapping("/internal/loyalty/redemptions")
    public ApiResponse<OrderRedemptionResponse> redeemForOrder(@Valid @RequestBody OrderRedemptionRequest request) {
        return ApiResponse.ok(loyaltyService.redeemForOrder(request));
    }

    /// order-service hoàn điểm đơn đã dùng khi đơn huỷ / hoàn tiền. Idempotent.
    @PostMapping("/internal/loyalty/redemptions/{orderId}/refund")
    public ApiResponse<OrderRedemptionRefundResponse> refundOrderRedemption(@PathVariable Long orderId) {
        return ApiResponse.ok(loyaltyService.refundOrderRedemption(orderId));
    }

    @GetMapping("/internal/loyalty/users/{userId}")
    public ApiResponse<LoyaltyAccountResponse> getByUserInternal(@PathVariable Long userId) {
        return ApiResponse.ok(loyaltyService.getByUser(userId));
    }
}

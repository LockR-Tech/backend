package com.huynqb.laundrylocker.loyalty.controller;

import com.huynqb.laundrylocker.common.dto.ApiResponse;
import com.huynqb.laundrylocker.loyalty.dto.*;
import com.huynqb.laundrylocker.loyalty.service.LoyaltyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class LoyaltyController {

    private final LoyaltyService loyaltyService;

    @GetMapping("/api/loyalty/users/{userId}")
    public ApiResponse<LoyaltyAccountResponse> getByUser(@PathVariable Long userId) {
        return ApiResponse.ok(loyaltyService.getByUser(userId));
    }

    @GetMapping("/api/loyalty/summary")
    public ApiResponse<LoyaltyAccountResponse> summary(@org.springframework.web.bind.annotation.RequestHeader("X-User-Id") Long userId) {
        return ApiResponse.ok(loyaltyService.getByUser(userId));
    }

    @GetMapping("/api/loyalty/points")
    public ApiResponse<LoyaltyAccountResponse> points(@org.springframework.web.bind.annotation.RequestHeader("X-User-Id") Long userId) {
        return summary(userId);
    }

    @GetMapping("/api/loyalty/points/history")
    public ApiResponse<List<PointTransactionResponse>> history(@org.springframework.web.bind.annotation.RequestHeader("X-User-Id") Long userId) {
        return ApiResponse.ok(loyaltyService.history(userId));
    }

    @PostMapping("/api/loyalty/redeem-points")
    public ApiResponse<LoyaltyAccountResponse> redeemPoints(@Valid @RequestBody RedeemPointsRequest request) {
        return ApiResponse.ok("POINTS_REDEEMED", "Points redeemed", loyaltyService.redeemPoints(request));
    }

    @PostMapping("/api/loyalty/redeem-stamp")
    public ApiResponse<LoyaltyAccountResponse> redeemStamp(@Valid @RequestBody RedeemStampRequest request) {
        return ApiResponse.ok("STAMP_REDEEMED", "Stamp redeemed", loyaltyService.redeemStamp(request));
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

    @PostMapping("/api/loyalty/points")
    public ApiResponse<LoyaltyAccountResponse> adjustPoints(@Valid @RequestBody AdjustPointsRequest request) {
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
    public ApiResponse<Map<String, Object>> adminStatistics() {
        return ApiResponse.ok(loyaltyService.statistics());
    }

    @GetMapping("/internal/loyalty/users/{userId}")
    public ApiResponse<LoyaltyAccountResponse> getByUserInternal(@PathVariable Long userId) {
        return ApiResponse.ok(loyaltyService.getByUser(userId));
    }
}

package com.huynqb.laundrylocker.payment.controller;

import com.huynqb.laundrylocker.common.dto.ApiResponse;
import com.huynqb.laundrylocker.payment.dto.WalletAdjustRequest;
import com.huynqb.laundrylocker.payment.dto.WalletResponse;
import com.huynqb.laundrylocker.payment.dto.WalletTransactionResponse;
import com.huynqb.laundrylocker.payment.service.WalletService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class WalletController {

    private final WalletService walletService;

    @GetMapping("/api/wallet")
    public ApiResponse<WalletResponse> balance(@RequestHeader("X-User-Id") Long userId) {
        return ApiResponse.ok("WALLET_OK", "Wallet balance", walletService.getBalance(userId));
    }

    @GetMapping("/api/wallet/transactions")
    public ApiResponse<List<WalletTransactionResponse>> transactions(
            @RequestHeader("X-User-Id") Long userId) {
        return ApiResponse.ok(walletService.history(userId));
    }

    @GetMapping("/internal/wallet/{userId}")
    public ApiResponse<WalletResponse> internalBalance(@PathVariable Long userId) {
        return ApiResponse.ok(walletService.getBalance(userId));
    }

    @GetMapping("/api/admin/wallet/{userId}")
    public ApiResponse<WalletResponse> adminBalance(@PathVariable Long userId) {
        return ApiResponse.ok(walletService.getBalance(userId));
    }

    @GetMapping("/api/admin/wallet/{userId}/transactions")
    public ApiResponse<List<WalletTransactionResponse>> adminTransactions(@PathVariable Long userId) {
        return ApiResponse.ok(walletService.history(userId));
    }

    @PostMapping("/api/admin/wallet/{userId}/adjust")
    public ApiResponse<WalletResponse> adjust(
            @PathVariable Long userId, @Valid @RequestBody WalletAdjustRequest request) {
        return ApiResponse.ok(
                "WALLET_ADJUSTED",
                "Wallet adjusted",
                walletService.adjust(userId, request.amount(), request.reason()));
    }

    @GetMapping("/api/wallet/withdrawable-balance")
    public ApiResponse<com.huynqb.laundrylocker.payment.dto.WithdrawableBalanceResponse> withdrawableBalance(
            @RequestHeader("X-User-Id") Long userId) {
        WalletResponse wallet = walletService.getBalance(userId);
        java.math.BigDecimal withdrawable = walletService.getWithdrawableBalance(userId);
        return ApiResponse.ok(new com.huynqb.laundrylocker.payment.dto.WithdrawableBalanceResponse(
                userId, wallet.balance(), withdrawable, wallet.currency()));
    }

    @PostMapping("/api/wallet/withdraw")
    public ApiResponse<com.huynqb.laundrylocker.payment.dto.WithdrawResponse> withdraw(
            @RequestHeader("X-User-Id") Long userId,
            @Valid @RequestBody com.huynqb.laundrylocker.payment.dto.WithdrawRequest request) {
        return ApiResponse.ok(
                "WITHDRAW_REQUESTED",
                "Yêu cầu rút tiền đã được tạo thành công và đang chờ xử lý",
                walletService.withdraw(userId, request));
    }

    @GetMapping("/api/wallet/withdrawals")
    public ApiResponse<List<com.huynqb.laundrylocker.payment.dto.WithdrawResponse>> myWithdrawals(
            @RequestHeader("X-User-Id") Long userId) {
        return ApiResponse.ok(walletService.listUserWithdrawals(userId));
    }

    @GetMapping({"/api/admin/withdrawals", "/api/admin/wallet/withdrawals"})
    public ApiResponse<List<com.huynqb.laundrylocker.payment.dto.WithdrawResponse>> adminWithdrawals(
            @RequestParam(required = false) String status) {
        return ApiResponse.ok(walletService.listAdminWithdrawals(status));
    }

    @PostMapping({"/api/admin/withdrawals/{id}/process", "/api/admin/wallet/withdrawals/{id}/process"})
    public ApiResponse<com.huynqb.laundrylocker.payment.dto.WithdrawResponse> processWithdrawal(
            @PathVariable Long id,
            @RequestHeader(value = "X-User-Id", defaultValue = "0") Long adminUserId,
            @Valid @RequestBody com.huynqb.laundrylocker.payment.dto.AdminWithdrawProcessRequest request) {
        if ("APPROVE".equalsIgnoreCase(request.action())) {
            return ApiResponse.ok(
                    "WITHDRAW_APPROVED",
                    "Đã phê duyệt yêu cầu rút tiền thành công",
                    walletService.adminApproveWithdrawal(id, adminUserId));
        } else if ("REJECT".equalsIgnoreCase(request.action())) {
            return ApiResponse.ok(
                    "WITHDRAW_REJECTED",
                    "Đã từ chối yêu cầu rút tiền và hoàn tiền vào ví",
                    walletService.adminRejectWithdrawal(id, adminUserId, request.reason()));
        } else {
            throw new com.huynqb.laundrylocker.common.exception.BusinessException(
                    "INVALID_ACTION", "Hành động không hợp lệ: " + request.action() + ". Chỉ chấp nhận APPROVE hoặc REJECT.");
        }
    }
}

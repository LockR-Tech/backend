package com.huynqb.laundrylocker.payment.controller;

import com.huynqb.laundrylocker.common.dto.ApiResponse;
import com.huynqb.laundrylocker.payment.dto.TransactionMethodTotalResponse;
import com.huynqb.laundrylocker.payment.dto.UserSpendingStatsResponse;
import com.huynqb.laundrylocker.payment.dto.WalletTransactionResponse;
import com.huynqb.laundrylocker.payment.service.TransactionHistoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/// Lịch sử giao dịch của khách (mục "Lịch sử giao dịch" trên app). Giữ đường dẫn `/api/wallet/...` cũ vì app
/// đang gọi; ví Lock.R đã gỡ nên không còn số dư, nạp, rút hay điều chỉnh — chỉ đọc lịch sử.
@RestController
@RequiredArgsConstructor
public class TransactionHistoryController {

    private final TransactionHistoryService historyService;

    @GetMapping("/api/wallet/transactions")
    public ApiResponse<List<WalletTransactionResponse>> transactions(@RequestHeader("X-User-Id") Long userId) {
        return ApiResponse.ok(historyService.history(userId));
    }

    @GetMapping("/api/wallet/spending-stats")
    public ApiResponse<UserSpendingStatsResponse> spendingStats(
            @RequestHeader("X-User-Id") Long userId,
            @RequestParam(required = false, defaultValue = "ALL") String period) {
        return ApiResponse.ok("SPENDING_STATS_OK", "Spending statistics", historyService.spendingStats(userId, period));
    }

    @GetMapping({"/api/wallet/total-by-method", "/api/wallet/transactions/total-by-method"})
    public ApiResponse<TransactionMethodTotalResponse> totalByMethod(
            @RequestHeader("X-User-Id") Long userId,
            @RequestParam(required = false, defaultValue = "ALL") String method,
            @RequestParam(required = false, defaultValue = "ALL") String period,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        return ApiResponse.ok("TOTAL_BY_METHOD_OK", "Total amount by transaction method",
                historyService.calculateTotalByMethod(userId, method, period, from, to));
    }
}

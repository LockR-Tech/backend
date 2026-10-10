package com.huynqb.laundrylocker.order.controller;

import com.huynqb.laundrylocker.common.dto.ApiResponse;
import com.huynqb.laundrylocker.common.dto.PageResponse;
import com.huynqb.laundrylocker.order.dto.admin.FeedbackDtos.FeedbackAnalyticsResponse;
import com.huynqb.laundrylocker.order.dto.admin.FeedbackDtos.FeedbackDetailResponse;
import com.huynqb.laundrylocker.order.dto.admin.FeedbackDtos.FeedbackReplyRequest;
import com.huynqb.laundrylocker.order.dto.admin.FeedbackDtos.FeedbackResponse;
import com.huynqb.laundrylocker.order.dto.admin.FeedbackDtos.FeedbackStatusRequest;
import com.huynqb.laundrylocker.order.dto.admin.FeedbackDtos.SatisfactionMetricsResponse;
import com.huynqb.laundrylocker.order.service.AdminFeedbackService;
import com.huynqb.laundrylocker.order.service.DashboardInsightService;
import com.huynqb.laundrylocker.order.service.DashboardInsightService.HourBucket;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/// Đánh giá dịch vụ (trang /admin/feedback) và số liệu dashboard phụ. Gateway chỉ cho ADMIN vào
/// /api/admin/**.
@RestController
@RequiredArgsConstructor
public class AdminFeedbackController {

    private final AdminFeedbackService feedbackService;
    private final DashboardInsightService insightService;

    /// `isResolved`: true = đã xử lý, false = chờ xử lý, bỏ trống = tất cả.
    @GetMapping("/api/admin/feedback")
    public ApiResponse<PageResponse<FeedbackResponse>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Integer minRating,
            @RequestParam(required = false) Integer maxRating,
            @RequestParam(required = false) Boolean isResolved) {
        return ApiResponse.ok(feedbackService.search(page, size, minRating, maxRating, isResolved));
    }

    @GetMapping("/api/admin/feedback/{id}")
    public ApiResponse<FeedbackDetailResponse> detail(@PathVariable Long id) {
        return ApiResponse.ok(feedbackService.detail(id));
    }

    @RequestMapping(value = "/api/admin/feedback/{id}/status", method = {RequestMethod.PATCH, RequestMethod.PUT})
    public ApiResponse<FeedbackResponse> updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody FeedbackStatusRequest request,
            @RequestHeader(value = "X-User-Id", required = false) Long adminId) {
        return ApiResponse.ok("FEEDBACK_STATUS_UPDATED", "Feedback status updated",
                feedbackService.updateStatus(id, request.status(), adminId));
    }

    @PostMapping("/api/admin/feedback/{id}/reply")
    public ApiResponse<FeedbackDetailResponse> reply(
            @PathVariable Long id,
            @Valid @RequestBody FeedbackReplyRequest request,
            @RequestHeader(value = "X-User-Id", required = false) Long adminId) {
        return ApiResponse.ok("FEEDBACK_REPLIED", "Feedback replied",
                feedbackService.reply(id, request.reply(), adminId));
    }

    /// `period`: day (14 ngày) · week (12 tuần) · month (12 tháng, mặc định) cho `trends`.
    @GetMapping("/api/admin/analytics/feedback")
    public ApiResponse<FeedbackAnalyticsResponse> analytics(@RequestParam(required = false) String period) {
        return ApiResponse.ok(feedbackService.analytics(period));
    }

    @GetMapping("/api/admin/analytics/satisfaction")
    public ApiResponse<SatisfactionMetricsResponse> satisfaction() {
        return ApiResponse.ok(feedbackService.satisfaction());
    }

    /// Số đơn theo giờ trong ngày (giờ Việt Nam); from/to yyyy-MM-dd như báo cáo doanh thu.
    @GetMapping("/api/admin/dashboard/peak-hours")
    public ApiResponse<List<HourBucket>> peakHours(
            @RequestParam(required = false) String from, @RequestParam(required = false) String to) {
        return ApiResponse.ok(insightService.peakHours(from, to));
    }
}

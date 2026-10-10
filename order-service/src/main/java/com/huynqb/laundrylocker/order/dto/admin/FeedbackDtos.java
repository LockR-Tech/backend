package com.huynqb.laundrylocker.order.dto.admin;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/// Đánh giá dịch vụ (bảng order_ratings) cho trang /admin/feedback. Tên field khớp type web
/// `FeedbackDTO`/`FeedbackDetailDTO`/`FeedbackAnalyticsDTO`/`SatisfactionMetricsDTO`.
/// Thông tin khách ghép từ user-service: null khi service đó lỗi. Thời gian theo múi giờ lưu
/// trữ (UTC), web tự đổi sang giờ Việt Nam.
public final class FeedbackDtos {

    private FeedbackDtos() {
    }

    public record FeedbackResponse(
            Long id,
            Long userId,
            String userName,
            String email,
            Integer rating,
            String comment,
            Long relatedOrderId,
            String orderCode,
            /** Loại đơn + mã đơn, vd "SEND · ORD-20261001-ABC". */
            String orderDescription,
            @JsonProperty("isResolved") boolean resolved,
            boolean replied,
            LocalDateTime createdAt,
            LocalDateTime updatedAt) {
    }

    public record FeedbackDetailResponse(
            Long id,
            Long userId,
            String userName,
            String userEmail,
            String userPhone,
            Integer rating,
            String comment,
            Long relatedOrderId,
            String orderCode,
            String serviceType,
            BigDecimal orderAmount,
            String adminReply,
            LocalDateTime repliedAt,
            /** Tên admin đã đánh dấu xử lý (hoặc "#id" khi không tra được). */
            String resolvedBy,
            LocalDateTime resolvedAt,
            LocalDateTime createdAt,
            LocalDateTime updatedAt) {
    }

    /// RESOLVED = đã xử lý, PENDING = mở lại.
    public record FeedbackStatusRequest(@NotBlank String status) {
    }

    public record FeedbackReplyRequest(@NotBlank @Size(max = 2000) String reply) {
    }

    public record FeedbackTrendPoint(LocalDate date, long count, double avgRating) {
    }

    public record FeedbackAnalyticsResponse(
            double averageRating,
            long totalFeedback,
            /** "1".."5" → số lượt (đủ 5 khoá). */
            Map<String, Long> ratingDistribution,
            long feedbackToday,
            long feedbackThisWeek,
            long feedbackThisMonth,
            /** day: 14 ngày gần nhất; week: 12 tuần (ngày đầu tuần); month: 12 tháng (ngày 1). */
            List<FeedbackTrendPoint> trends,
            long unresolvedCount) {
    }

    public record SatisfactionMetricsResponse(
            /** Điểm trung bình quy về thang 100. */
            double overallSatisfactionScore,
            /** Kiểu NPS trên thang 5 sao: % 5 sao − % 1–3 sao (−100..100). */
            double npsScore,
            /** % đánh giá 4–5 sao. */
            double positivePercentage,
            /** % đánh giá 1–2 sao. */
            double negativePercentage,
            /** Loại khiếu nại nhiều nhất 90 ngày qua; null nếu chưa có. */
            String mostCommonComplaint,
            /** Loại đơn có điểm trung bình cao nhất; null nếu chưa có. */
            String topServiceQuality,
            /** Loại đơn → điểm trung bình (1–5). */
            Map<String, Double> departmentScores) {
    }
}

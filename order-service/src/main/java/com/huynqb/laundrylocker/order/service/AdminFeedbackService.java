package com.huynqb.laundrylocker.order.service;

import com.huynqb.laundrylocker.common.dto.NotificationRequest;
import com.huynqb.laundrylocker.common.dto.PageResponse;
import com.huynqb.laundrylocker.common.dto.UserSummary;
import com.huynqb.laundrylocker.common.exception.BusinessException;
import com.huynqb.laundrylocker.common.exception.NotFoundException;
import com.huynqb.laundrylocker.common.util.BusinessTime;
import com.huynqb.laundrylocker.order.client.NotificationClient;
import com.huynqb.laundrylocker.order.dto.admin.FeedbackDtos.FeedbackAnalyticsResponse;
import com.huynqb.laundrylocker.order.dto.admin.FeedbackDtos.FeedbackDetailResponse;
import com.huynqb.laundrylocker.order.dto.admin.FeedbackDtos.FeedbackResponse;
import com.huynqb.laundrylocker.order.dto.admin.FeedbackDtos.FeedbackTrendPoint;
import com.huynqb.laundrylocker.order.dto.admin.FeedbackDtos.SatisfactionMetricsResponse;
import com.huynqb.laundrylocker.order.model.LockerOrder;
import com.huynqb.laundrylocker.order.model.OrderRating;
import com.huynqb.laundrylocker.order.repository.LockerOrderRepository;
import com.huynqb.laundrylocker.order.repository.OrderRatingRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/// Đánh giá dịch vụ (order_ratings) cho admin: danh sách, chi tiết, đánh dấu xử lý, trả lời khách
/// và số liệu cho tab "Chỉ số & Thống kê". Thông tin khách ghép từ user-service, lỗi thì để null.
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminFeedbackService {

    static final int MAX_PAGE_SIZE = 100;
    static final String RESOLVED = "RESOLVED";
    static final String PENDING = "PENDING";

    private final OrderRatingRepository ratings;
    private final LockerOrderRepository orders;
    private final AdminReferenceResolver references;
    private final NotificationClient notificationClient;

    private BusinessTime time = BusinessTime.system();

    void setTime(BusinessTime time) {
        this.time = time;
    }

    @Transactional(readOnly = true)
    public PageResponse<FeedbackResponse> search(
            int page, int size, Integer minRating, Integer maxRating, Boolean resolved) {
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        Specification<OrderRating> spec = (root, query, cb) -> {
            List<Predicate> where = new ArrayList<>();
            if (minRating != null) {
                where.add(cb.greaterThanOrEqualTo(root.get("rating"), minRating));
            }
            if (maxRating != null) {
                where.add(cb.lessThanOrEqualTo(root.get("rating"), maxRating));
            }
            if (resolved != null) {
                where.add(cb.equal(root.get("resolved"), resolved));
            }
            return cb.and(where.toArray(Predicate[]::new));
        };
        Page<OrderRating> result = ratings.findAll(spec, PageRequest.of(
                Math.max(page, 0), safeSize, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))));
        List<OrderRating> rows = result.getContent();
        Map<Long, LockerOrder> orderById = ordersOf(rows);
        AdminReferenceResolver.Lookup<UserSummary> users =
                references.users(rows.stream().map(OrderRating::getUserId).collect(Collectors.toSet()));
        List<FeedbackResponse> content = rows.stream()
                .map(r -> toResponse(r, orderById.get(r.getOrderId()), users.get(r.getUserId())))
                .toList();
        return new PageResponse<>(
                content, result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    @Transactional(readOnly = true)
    public FeedbackDetailResponse detail(Long id) {
        return toDetail(find(id));
    }

    @Transactional
    public FeedbackResponse updateStatus(Long id, String status, Long adminId) {
        String normalized = status == null ? "" : status.trim().toUpperCase(Locale.ROOT);
        if (!RESOLVED.equals(normalized) && !PENDING.equals(normalized)) {
            throw new BusinessException("FEEDBACK_STATUS_INVALID", "Trạng thái chỉ nhận RESOLVED hoặc PENDING");
        }
        OrderRating rating = find(id);
        if (RESOLVED.equals(normalized)) {
            rating.setResolved(true);
            rating.setResolvedAt(time.now());
            rating.setResolvedBy(adminId);
        } else {
            rating.setResolved(false);
            rating.setResolvedAt(null);
            rating.setResolvedBy(null);
        }
        OrderRating saved = ratings.save(rating);
        LockerOrder order = orders.findById(saved.getOrderId()).orElse(null);
        return toResponse(saved, order, references.users(Set.of(saved.getUserId())).get(saved.getUserId()));
    }

    /// Lưu câu trả lời và báo khách qua thông báo (lỗi gửi thông báo không làm hỏng việc lưu).
    @Transactional
    public FeedbackDetailResponse reply(Long id, String reply, Long adminId) {
        String text = reply == null ? "" : reply.trim();
        if (text.isEmpty()) {
            throw new BusinessException("FEEDBACK_REPLY_EMPTY", "Nội dung trả lời không được để trống");
        }
        OrderRating rating = find(id);
        rating.setAdminReply(text);
        rating.setRepliedAt(time.now());
        rating.setRepliedBy(adminId);
        OrderRating saved = ratings.save(rating);
        try {
            notificationClient.requestNotification(new NotificationRequest(
                    saved.getUserId(), "Lock.R đã phản hồi đánh giá của bạn",
                    text.length() > 200 ? text.substring(0, 197) + "..." : text,
                    "FEEDBACK_REPLY", saved.getOrderId(), "ORDER"));
        } catch (Exception ex) {
            log.warn("Could not notify user {} about feedback reply {}: {}", saved.getUserId(), id, ex.getMessage());
        }
        return toDetail(saved);
    }

    @Transactional(readOnly = true)
    public FeedbackAnalyticsResponse analytics(String period) {
        Map<String, Long> distribution = distribution();
        long total = distribution.values().stream().mapToLong(Long::longValue).sum();
        LocalDate today = time.today();
        return new FeedbackAnalyticsResponse(
                round(average(distribution, total), 2),
                total,
                distribution,
                ratings.countByCreatedAtGreaterThanEqual(time.startOfDay(today)),
                ratings.countByCreatedAtGreaterThanEqual(time.startOfDay(time.startOfWeek(today))),
                ratings.countByCreatedAtGreaterThanEqual(time.startOfDay(time.startOfMonth(today))),
                trends(period, today),
                ratings.countByResolvedFalse());
    }

    @Transactional(readOnly = true)
    public SatisfactionMetricsResponse satisfaction() {
        Map<String, Long> distribution = distribution();
        long total = distribution.values().stream().mapToLong(Long::longValue).sum();
        Map<String, Double> byService = new LinkedHashMap<>();
        String topService = null;
        double topScore = -1;
        for (Object[] row : ratings.averageByOrderType()) {
            String type = (String) row[0];
            double avg = round(((Number) row[1]).doubleValue(), 2);
            byService.put(type, avg);
            if (avg > topScore) {
                topScore = avg;
                topService = type;
            }
        }
        List<Object[]> complaints = ratings.complaintTypesSince(time.now().minusDays(90));
        String topComplaint = complaints.isEmpty() ? null : (String) complaints.get(0)[0];
        if (total == 0) {
            return new SatisfactionMetricsResponse(0, 0, 0, 0, topComplaint, topService, byService);
        }
        double five = percent(distribution.get("5"), total);
        double lowThree = percent(distribution.get("1") + distribution.get("2") + distribution.get("3"), total);
        return new SatisfactionMetricsResponse(
                round(average(distribution, total) / 5 * 100, 1),
                round(five - lowThree, 1),
                round(percent(distribution.get("4") + distribution.get("5"), total), 1),
                round(percent(distribution.get("1") + distribution.get("2"), total), 1),
                topComplaint,
                topService,
                byService);
    }

    private OrderRating find(Long id) {
        return ratings.findById(id).orElseThrow(() -> new NotFoundException("Feedback", id));
    }

    /// "1".."5" luôn đủ khoá, kể cả khi bằng 0.
    private Map<String, Long> distribution() {
        Map<String, Long> result = new LinkedHashMap<>();
        for (int star = 1; star <= 5; star++) {
            result.put(String.valueOf(star), 0L);
        }
        for (Object[] row : ratings.countByRating()) {
            int star = ((Number) row[0]).intValue();
            if (star >= 1 && star <= 5) {
                result.put(String.valueOf(star), ((Number) row[1]).longValue());
            }
        }
        return result;
    }

    private static double average(Map<String, Long> distribution, long total) {
        if (total == 0) {
            return 0;
        }
        long sum = 0;
        for (Map.Entry<String, Long> entry : distribution.entrySet()) {
            sum += Long.parseLong(entry.getKey()) * entry.getValue();
        }
        return (double) sum / total;
    }

    /// Theo ngày (14 ngày), tuần (12 tuần) hoặc tháng (12 tháng) gần nhất — đủ mốc, mốc trống = 0.
    private List<FeedbackTrendPoint> trends(String period, LocalDate today) {
        String unit = period == null ? "month" : period.trim().toLowerCase(Locale.ROOT);
        Function<LocalDate, LocalDate> bucket;
        List<LocalDate> keys = new ArrayList<>();
        switch (unit) {
            case "day" -> {
                bucket = date -> date;
                for (int i = 13; i >= 0; i--) {
                    keys.add(today.minusDays(i));
                }
            }
            case "week" -> {
                bucket = time::startOfWeek;
                LocalDate thisWeek = time.startOfWeek(today);
                for (int i = 11; i >= 0; i--) {
                    keys.add(thisWeek.minusWeeks(i));
                }
            }
            default -> {
                bucket = time::startOfMonth;
                LocalDate thisMonth = time.startOfMonth(today);
                for (int i = 11; i >= 0; i--) {
                    keys.add(thisMonth.minusMonths(i));
                }
            }
        }
        Map<LocalDate, long[]> sums = new LinkedHashMap<>();
        keys.forEach(key -> sums.put(key, new long[2]));
        for (OrderRating rating : ratings.findByCreatedAtGreaterThanEqual(time.startOfDay(keys.get(0)))) {
            long[] cell = sums.get(bucket.apply(time.businessDate(rating.getCreatedAt())));
            if (cell != null) {
                cell[0]++;
                cell[1] += rating.getRating();
            }
        }
        return sums.entrySet().stream()
                .map(e -> new FeedbackTrendPoint(
                        e.getKey(), e.getValue()[0],
                        e.getValue()[0] == 0 ? 0 : round((double) e.getValue()[1] / e.getValue()[0], 2)))
                .toList();
    }

    private Map<Long, LockerOrder> ordersOf(List<OrderRating> rows) {
        Set<Long> ids = new HashSet<>();
        rows.forEach(r -> ids.add(r.getOrderId()));
        return orders.findAllById(ids).stream()
                .collect(Collectors.toMap(LockerOrder::getId, Function.identity(), (a, b) -> a));
    }

    private FeedbackResponse toResponse(OrderRating r, LockerOrder order, UserSummary user) {
        return new FeedbackResponse(
                r.getId(),
                r.getUserId(),
                user == null ? null : user.fullName(),
                user == null ? null : user.email(),
                r.getRating(),
                r.getComment(),
                r.getOrderId(),
                order == null ? null : order.getOrderCode(),
                order == null ? null : order.getType() + " · " + order.getOrderCode(),
                r.isResolved(),
                r.getAdminReply() != null,
                r.getCreatedAt(),
                r.getUpdatedAt());
    }

    private FeedbackDetailResponse toDetail(OrderRating r) {
        LockerOrder order = orders.findById(r.getOrderId()).orElse(null);
        Set<Long> userIds = new HashSet<>();
        userIds.add(r.getUserId());
        if (r.getResolvedBy() != null) {
            userIds.add(r.getResolvedBy());
        }
        AdminReferenceResolver.Lookup<UserSummary> users = references.users(userIds);
        UserSummary customer = users.get(r.getUserId());
        String resolvedBy = null;
        if (r.getResolvedBy() != null) {
            UserSummary admin = users.get(r.getResolvedBy());
            resolvedBy = admin != null && admin.fullName() != null ? admin.fullName() : "#" + r.getResolvedBy();
        }
        return new FeedbackDetailResponse(
                r.getId(),
                r.getUserId(),
                customer == null ? null : customer.fullName(),
                customer == null ? null : customer.email(),
                customer == null ? null : customer.phoneNumber(),
                r.getRating(),
                r.getComment(),
                r.getOrderId(),
                order == null ? null : order.getOrderCode(),
                order == null ? null : order.getType(),
                order == null ? null : order.getTotalPrice(),
                r.getAdminReply(),
                r.getRepliedAt(),
                resolvedBy,
                r.getResolvedAt(),
                r.getCreatedAt(),
                r.getUpdatedAt());
    }

    private static double percent(long part, long total) {
        return total == 0 ? 0 : part * 100.0 / total;
    }

    private static double round(double value, int scale) {
        return BigDecimal.valueOf(value).setScale(scale, RoundingMode.HALF_UP).doubleValue();
    }
}

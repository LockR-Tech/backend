package com.huynqb.laundrylocker.payment.service;

import com.huynqb.laundrylocker.common.util.BusinessTime;
import com.huynqb.laundrylocker.common.util.BusinessTime.DateRange;
import com.huynqb.laundrylocker.payment.dto.admin.PaymentStatsResponse;
import com.huynqb.laundrylocker.payment.dto.admin.PaymentStatsResponse.Bucket;
import com.huynqb.laundrylocker.payment.dto.admin.PaymentStatsResponse.Changes;
import com.huynqb.laundrylocker.payment.dto.admin.PaymentStatsResponse.KindStats;
import com.huynqb.laundrylocker.payment.dto.admin.PaymentStatsResponse.PeriodStats;
import com.huynqb.laundrylocker.payment.model.PaymentRecord;
import com.huynqb.laundrylocker.payment.model.RefundRecord;
import com.huynqb.laundrylocker.payment.repository.PaymentRepository;
import com.huynqb.laundrylocker.payment.repository.RefundRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.function.Predicate;

import static com.huynqb.laundrylocker.payment.repository.PaymentSpecifications.*;
import static com.huynqb.laundrylocker.payment.service.PaymentReportRules.amount;

/// Thống kê cho thẻ tổng quan trang /admin/payments. Một truy vấn payments + một truy vấn
/// refunds cho cả cửa sổ (kỳ này, kỳ trước, hôm nay, hôm qua) rồi chia nhóm trong bộ nhớ.
@Service
@RequiredArgsConstructor
public class PaymentStatsService {

    private final PaymentRepository paymentRepository;
    private final RefundRepository refundRepository;

    private BusinessTime time = BusinessTime.system();

    void setTime(BusinessTime time) {
        this.time = time;
    }

    @Transactional(readOnly = true)
    public PaymentStatsResponse stats(String from, String to) {
        DateRange current = time.dateRange(from, to);
        DateRange previous = current.previous();
        LocalDate todayDate = time.today();
        DateRange today = new DateRange(todayDate, todayDate);
        DateRange yesterday = new DateRange(todayDate.minusDays(1), todayDate.minusDays(1));

        LocalDate windowStart = min(previous.from(), yesterday.from());
        LocalDate windowEnd = max(current.to(), today.to());
        LocalDateTime start = time.startOfDay(windowStart);
        LocalDateTime end = time.startOfDay(windowEnd.plusDays(1));

        List<PaymentRecord> payments = paymentRepository.findAll(all(paymentCreatedBetween(start, end)));
        List<RefundRecord> refunds = refundRepository.findAll(all(completedOrderRefundBetween(start, end)));

        PeriodStats currentStats = period(current, payments, refunds);
        PeriodStats previousStats = period(previous, payments, refunds);
        PeriodStats todayStats = period(today, payments, refunds);
        PeriodStats yesterdayStats = period(yesterday, payments, refunds);
        return new PaymentStatsResponse(
                current.from(), current.to(), previous.from(), previous.to(),
                currentStats, previousStats, todayStats, yesterdayStats,
                changes(currentStats, previousStats), changes(todayStats, yesterdayStats));
    }

    private PeriodStats period(DateRange range, List<PaymentRecord> allPayments, List<RefundRecord> allRefunds) {
        List<PaymentRecord> payments = allPayments.stream()
                .filter(p -> within(range, time.businessDate(p.getCreatedAt())))
                .toList();
        List<RefundRecord> refunds = allRefunds.stream()
                .filter(r -> within(range, time.businessDate(PaymentReportRules.refundedAt(r))))
                .toList();

        long completedCount = count(payments, PaymentReportRules::isCompleted);
        long total = payments.size();
        BigDecimal refundAmount = sum(refunds.stream().map(RefundRecord::getAmount).toList());
        KindStats orders = kind(payments, p -> !PaymentReportRules.isTopup(p));
        KindStats topups = kind(payments, PaymentReportRules::isTopup);
        return new PeriodStats(
                range.from(),
                range.to(),
                total,
                sumPayments(payments, p -> true),
                completedCount,
                sumPayments(payments, PaymentReportRules::isCompleted),
                count(payments, status("PENDING")),
                sumPayments(payments, status("PENDING")),
                count(payments, status("FAILED")),
                sumPayments(payments, status("FAILED")),
                count(payments, other()),
                sumPayments(payments, other()),
                total == 0 ? null : BigDecimal.valueOf(completedCount * 100.0 / total).setScale(2, RoundingMode.HALF_UP),
                orders,
                topups,
                refunds.size(),
                refundAmount,
                orders.completedAmount().subtract(refundAmount),
                buckets(payments, p -> upper(p.getStatus())),
                buckets(payments, p -> upper(p.getMethod())));
    }

    private KindStats kind(List<PaymentRecord> payments, Predicate<PaymentRecord> filter) {
        List<PaymentRecord> subset = payments.stream().filter(filter).toList();
        return new KindStats(
                subset.size(),
                sumPayments(subset, p -> true),
                count(subset, PaymentReportRules::isCompleted),
                sumPayments(subset, PaymentReportRules::isCompleted));
    }

    private List<Bucket> buckets(List<PaymentRecord> payments, Function<PaymentRecord, String> key) {
        Map<String, List<PaymentRecord>> groups = new TreeMap<>();
        payments.forEach(p -> groups.computeIfAbsent(key.apply(p), k -> new java.util.ArrayList<>()).add(p));
        return groups.entrySet().stream()
                .map(e -> new Bucket(
                        e.getKey(),
                        e.getValue().size(),
                        sumPayments(e.getValue(), p -> true),
                        count(e.getValue(), PaymentReportRules::isCompleted),
                        sumPayments(e.getValue(), PaymentReportRules::isCompleted)))
                .sorted((a, b) -> b.amount().compareTo(a.amount()))
                .toList();
    }

    private Changes changes(PeriodStats cur, PeriodStats prev) {
        BigDecimal successPoints = cur.successRate() == null || prev.successRate() == null
                ? null
                : cur.successRate().subtract(prev.successRate());
        return new Changes(
                pct(BigDecimal.valueOf(cur.totalCount()), BigDecimal.valueOf(prev.totalCount())),
                pct(cur.totalAmount(), prev.totalAmount()),
                pct(BigDecimal.valueOf(cur.completedCount()), BigDecimal.valueOf(prev.completedCount())),
                pct(cur.completedAmount(), prev.completedAmount()),
                pct(cur.refundAmount(), prev.refundAmount()),
                pct(cur.netCollectedAmount(), prev.netCollectedAmount()),
                successPoints);
    }

    static BigDecimal pct(BigDecimal current, BigDecimal previous) {
        if (previous == null || previous.signum() == 0) {
            return null;
        }
        return current.subtract(previous)
                .multiply(BigDecimal.valueOf(100))
                .divide(previous.abs(), 2, RoundingMode.HALF_UP);
    }

    private static Predicate<PaymentRecord> status(String status) {
        return p -> status.equalsIgnoreCase(p.getStatus());
    }

    private static Predicate<PaymentRecord> other() {
        return p -> !java.util.Set.of("COMPLETED", "PENDING", "FAILED").contains(upper(p.getStatus()));
    }

    private static long count(List<PaymentRecord> payments, Predicate<PaymentRecord> filter) {
        return payments.stream().filter(filter).count();
    }

    private static BigDecimal sumPayments(List<PaymentRecord> payments, Predicate<PaymentRecord> filter) {
        return payments.stream().filter(filter).map(p -> amount(p.getAmount())).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BigDecimal sum(Collection<BigDecimal> values) {
        return values.stream().map(PaymentReportRules::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static boolean within(DateRange range, LocalDate date) {
        return date != null && !date.isBefore(range.from()) && !date.isAfter(range.to());
    }

    private static String upper(String value) {
        return value == null ? "UNKNOWN" : value.toUpperCase(Locale.ROOT);
    }

    private static LocalDate min(LocalDate a, LocalDate b) {
        return a.isBefore(b) ? a : b;
    }

    private static LocalDate max(LocalDate a, LocalDate b) {
        return a.isAfter(b) ? a : b;
    }
}

package com.huynqb.laundrylocker.payment.service;

import com.huynqb.laundrylocker.common.exception.BusinessException;
import com.huynqb.laundrylocker.payment.dto.internal.CollectedPaymentsResponse;
import com.huynqb.laundrylocker.payment.dto.internal.CollectedPaymentsResponse.CollectedPayment;
import com.huynqb.laundrylocker.payment.dto.internal.CollectedPaymentsResponse.CollectedRefund;
import com.huynqb.laundrylocker.payment.dto.internal.CollectedPaymentsResponse.OrderPaidTotal;
import com.huynqb.laundrylocker.payment.dto.internal.OrderPaymentSummary;
import com.huynqb.laundrylocker.payment.model.PaymentRecord;
import com.huynqb.laundrylocker.payment.model.RefundRecord;
import com.huynqb.laundrylocker.payment.repository.PaymentRepository;
import com.huynqb.laundrylocker.payment.repository.PaymentSpecifications;
import com.huynqb.laundrylocker.payment.repository.RefundRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.huynqb.laundrylocker.payment.service.PaymentReportRules.amount;

/// Số liệu thanh toán cho order-service (trang admin đơn hàng + báo cáo doanh thu).
@Service
@RequiredArgsConstructor
public class InternalPaymentReportService {

    static final int MAX_ORDER_IDS = 500;
    static final long MAX_RANGE_DAYS = 1100;

    private final PaymentRepository paymentRepository;
    private final RefundRepository refundRepository;

    @Transactional(readOnly = true)
    public List<OrderPaymentSummary> orderSummaries(Collection<Long> orderIds) {
        List<Long> ids = orderIds == null
                ? List.of()
                : orderIds.stream().filter(Objects::nonNull).filter(id -> id > 0).distinct().toList();
        if (ids.isEmpty()) {
            return List.of();
        }
        if (ids.size() > MAX_ORDER_IDS) {
            throw new BusinessException("TOO_MANY_IDS", "At most " + MAX_ORDER_IDS + " order ids per request");
        }
        Map<Long, List<PaymentRecord>> paymentsByOrder = paymentRepository.findByOrderIdIn(ids).stream()
                .filter(p -> !PaymentReportRules.isTopup(p))
                .collect(Collectors.groupingBy(PaymentRecord::getOrderId));
        Map<Long, BigDecimal> refundedByOrder = refundRepository.findByOrderIdIn(ids).stream()
                .filter(PaymentReportRules::isCompletedOrderRefund)
                .collect(Collectors.groupingBy(RefundRecord::getOrderId,
                        Collectors.reducing(BigDecimal.ZERO, r -> amount(r.getAmount()), BigDecimal::add)));

        return ids.stream()
                .filter(id -> paymentsByOrder.containsKey(id) || refundedByOrder.containsKey(id))
                .map(id -> summarize(id, paymentsByOrder.getOrDefault(id, List.of()), refundedByOrder.get(id)))
                .toList();
    }

    @Transactional(readOnly = true)
    public CollectedPaymentsResponse collected(String from, String to, Long userId) {
        LocalDateTime start = parse(from, "from");
        LocalDateTime end = parse(to, "to");
        if (!end.isAfter(start)) {
            throw new BusinessException("INVALID_DATE_RANGE", "to must be after from");
        }
        if (Duration.between(start, end).toDays() > MAX_RANGE_DAYS) {
            throw new BusinessException("INVALID_DATE_RANGE", "range too large");
        }

        List<PaymentRecord> payments = paymentRepository.findAll(
                PaymentSpecifications.all(
                        PaymentSpecifications.paymentPaidBetween(start, end),
                        PaymentSpecifications.paymentKind(PaymentReportRules.KIND_ORDER),
                        PaymentSpecifications.paymentUser(userId)),
                Sort.by("id"));
        List<RefundRecord> refunds = refundRepository.findAll(
                PaymentSpecifications.all(
                        PaymentSpecifications.completedOrderRefundBetween(start, end),
                        PaymentSpecifications.refundUser(userId)),
                Sort.by("id"));

        Map<Long, PaymentRecord> refundPayments = refunds.isEmpty()
                ? Map.of()
                : paymentRepository.findAllById(refunds.stream().map(RefundRecord::getPaymentId).distinct().toList())
                        .stream().collect(Collectors.toMap(PaymentRecord::getId, Function.identity()));

        List<Long> orderIds = payments.stream().map(PaymentRecord::getOrderId).distinct().toList();
        Map<Long, BigDecimal> paidTotals = orderIds.isEmpty()
                ? Map.of()
                : paymentRepository.findByOrderIdIn(orderIds).stream()
                        .filter(PaymentReportRules::isCollectedOrderPayment)
                        .collect(Collectors.groupingBy(PaymentRecord::getOrderId, LinkedHashMap::new,
                                Collectors.reducing(BigDecimal.ZERO, p -> amount(p.getAmount()), BigDecimal::add)));

        return new CollectedPaymentsResponse(
                payments.stream()
                        .map(p -> new CollectedPayment(
                                p.getId(), p.getOrderId(), p.getUserId(), amount(p.getAmount()),
                                p.getMethod(), PaymentReportRules.paidAt(p)))
                        .toList(),
                refunds.stream()
                        .map(r -> {
                            PaymentRecord payment = refundPayments.get(r.getPaymentId());
                            return new CollectedRefund(
                                    r.getId(), r.getPaymentId(), r.getOrderId(),
                                    payment == null ? null : payment.getUserId(),
                                    amount(r.getAmount()),
                                    payment == null ? null : payment.getMethod(),
                                    PaymentReportRules.refundedAt(r));
                        })
                        .toList(),
                orderIds.stream()
                        .map(id -> new OrderPaidTotal(id, paidTotals.getOrDefault(id, BigDecimal.ZERO)))
                        .toList());
    }

    private OrderPaymentSummary summarize(Long orderId, List<PaymentRecord> payments, BigDecimal refunded) {
        Comparator<PaymentRecord> newestFirst = Comparator
                .comparing(PaymentRecord::getCreatedAt, Comparator.nullsFirst(Comparator.naturalOrder()))
                .thenComparing(PaymentRecord::getId, Comparator.nullsFirst(Comparator.naturalOrder()))
                .reversed();
        PaymentRecord latest = payments.stream().sorted(newestFirst).findFirst().orElse(null);
        List<PaymentRecord> completed = payments.stream().filter(PaymentReportRules::isCompleted).toList();
        PaymentRecord lastPaid = completed.stream()
                .max(Comparator.comparing(PaymentReportRules::paidAt, Comparator.nullsFirst(Comparator.naturalOrder()))
                        .thenComparing(PaymentRecord::getId, Comparator.nullsFirst(Comparator.naturalOrder())))
                .orElse(null);
        BigDecimal paid = completed.stream().map(p -> amount(p.getAmount())).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new OrderPaymentSummary(
                orderId,
                payments.size(),
                latest == null ? null : latest.getId(),
                latest == null ? null : latest.getMethod(),
                latest == null ? null : latest.getStatus(),
                latest == null ? null : latest.getAmount(),
                latest == null ? null : latest.getCreatedAt(),
                paid,
                lastPaid == null ? null : lastPaid.getMethod(),
                lastPaid == null ? null : PaymentReportRules.paidAt(lastPaid),
                refunded == null ? BigDecimal.ZERO : refunded);
    }

    private LocalDateTime parse(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new BusinessException("INVALID_DATE", name + " is required");
        }
        try {
            return LocalDateTime.parse(value.trim());
        } catch (DateTimeParseException ex) {
            throw new BusinessException("INVALID_DATE", name + " must be ISO local date-time");
        }
    }
}

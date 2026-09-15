package com.huynqb.laundrylocker.payment.service;

import com.huynqb.laundrylocker.common.dto.PageResponse;
import com.huynqb.laundrylocker.common.dto.UserSummary;
import com.huynqb.laundrylocker.common.exception.BusinessException;
import com.huynqb.laundrylocker.common.exception.NotFoundException;
import com.huynqb.laundrylocker.common.util.BusinessTime;
import com.huynqb.laundrylocker.payment.dto.admin.AdminPaymentDetailResponse;
import com.huynqb.laundrylocker.payment.dto.admin.AdminPaymentResponse;
import com.huynqb.laundrylocker.payment.dto.admin.AdminRefundResponse;
import com.huynqb.laundrylocker.payment.dto.admin.AdminWalletTransactionResponse;
import com.huynqb.laundrylocker.payment.dto.admin.OrderBrief;
import com.huynqb.laundrylocker.payment.dto.admin.OrderRef;
import com.huynqb.laundrylocker.payment.dto.admin.PersonRef;
import com.huynqb.laundrylocker.payment.model.PaymentRecord;
import com.huynqb.laundrylocker.payment.model.RefundRecord;
import com.huynqb.laundrylocker.payment.model.WalletTransaction;
import com.huynqb.laundrylocker.payment.repository.PaymentRepository;
import com.huynqb.laundrylocker.payment.repository.RefundRepository;
import com.huynqb.laundrylocker.payment.repository.WalletTransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.huynqb.laundrylocker.payment.repository.PaymentSpecifications.*;
import static com.huynqb.laundrylocker.payment.service.PaymentReportRules.amount;

/// Danh sách/chi tiết giao dịch, hoàn tiền, biến động ví cho trang /admin/payments.
@Service
@RequiredArgsConstructor
public class AdminPaymentQueryService {

    public static final int MAX_PAGE_SIZE = 100;
    static final String ORDER_PAYMENT_REF_PREFIX = "ORDERPAY-";

    private final PaymentRepository paymentRepository;
    private final RefundRepository refundRepository;
    private final WalletTransactionRepository walletTransactionRepository;
    private final PaymentReferenceResolver resolver;

    private BusinessTime time = BusinessTime.system();

    void setTime(BusinessTime time) {
        this.time = time;
    }

    public record PaymentCriteria(
            int page, int size, List<String> statuses, List<String> methods, String kind, Boolean includeTopups,
            String from, String to, Long userId, Long orderId, String q, String sort) {
    }

    public record RefundCriteria(
            int page, int size, List<String> statuses, String from, String to, Long orderId, Long paymentId,
            Long userId, String sort) {
    }

    public record WalletCriteria(
            int page, int size, Long userId, List<String> types, List<String> sources, String from, String to,
            String q, String sort) {
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminPaymentResponse> searchPayments(PaymentCriteria c) {
        String kind = c.kind();
        if (Boolean.FALSE.equals(c.includeTopups()) && (kind == null || kind.isBlank() || "ALL".equalsIgnoreCase(kind))) {
            kind = PaymentReportRules.KIND_ORDER;
        }
        if (kind != null && !kind.isBlank()
                && !Set.of("ALL", PaymentReportRules.KIND_ORDER, PaymentReportRules.KIND_TOPUP).contains(kind.toUpperCase())) {
            throw new BusinessException("INVALID_KIND", "kind must be ALL, ORDER or TOPUP");
        }
        Page<PaymentRecord> page = paymentRepository.findAll(
                all(paymentStatusIn(c.statuses()),
                        paymentMethodIn(c.methods()),
                        paymentKind(kind),
                        paymentCreatedBetween(time.parseFrom(c.from()), time.parseToExclusive(c.to())),
                        paymentUser(c.userId()),
                        paymentOrder(c.orderId()),
                        paymentReferenceLike(c.q())),
                pageable(c.page(), c.size(), c.sort(), Set.of("createdAt", "updatedAt", "amount", "id"), "createdAt"));
        return page(page, toPaymentResponses(page.getContent()));
    }

    @Transactional(readOnly = true)
    public AdminPaymentDetailResponse paymentDetail(Long id) {
        PaymentRecord payment = paymentRepository.findById(id).orElseThrow(() -> new NotFoundException("Payment", id));
        List<PaymentRecord> orderPayments = PaymentReportRules.isTopup(payment)
                ? List.of()
                : paymentRepository.findByOrderId(payment.getOrderId()).stream()
                        .filter(p -> !p.getId().equals(payment.getId()))
                        .sorted((a, b) -> compareDesc(a.getCreatedAt(), b.getCreatedAt()))
                        .toList();
        List<PaymentRecord> all = new ArrayList<>();
        all.add(payment);
        all.addAll(orderPayments);
        List<AdminPaymentResponse> responses = toPaymentResponses(all);

        List<RefundRecord> refunds = refundRepository.findByPaymentIdIn(List.of(payment.getId()));
        List<WalletTransaction> walletTransactions = new ArrayList<>();
        if (PaymentReportRules.METHOD_TOPUP.equalsIgnoreCase(payment.getMethod()) && payment.getReferenceId() != null) {
            walletTransactionRepository.findFirstBySourceAndReferenceId(WalletService.SOURCE_TOPUP, payment.getReferenceId())
                    .ifPresent(walletTransactions::add);
        } else if ("WALLET".equalsIgnoreCase(payment.getMethod())) {
            walletTransactionRepository
                    .findFirstBySourceAndReferenceId(WalletService.SOURCE_ORDER_PAYMENT, ORDER_PAYMENT_REF_PREFIX + payment.getOrderId())
                    .filter(tx -> Objects.equals(tx.getUserId(), payment.getUserId()))
                    .ifPresent(walletTransactions::add);
        }
        Map<Long, UserSummary> users = resolver.users(refunds.stream().map(RefundRecord::getProcessedByUserId).toList());
        return new AdminPaymentDetailResponse(
                responses.get(0),
                refunds.stream()
                        .map(r -> toRefund(r, payment, responses.get(0).order(), responses.get(0).customer(), users))
                        .toList(),
                walletTransactions.stream().map(tx -> toWallet(tx, responses.get(0).customer())).toList(),
                responses.subList(1, responses.size()));
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminRefundResponse> searchRefunds(RefundCriteria c) {
        Page<RefundRecord> page = refundRepository.findAll(
                all(refundStatusIn(c.statuses()),
                        refundRequestedBetween(time.parseFrom(c.from()), time.parseToExclusive(c.to())),
                        refundOrder(c.orderId()),
                        refundPayment(c.paymentId()),
                        refundUser(c.userId())),
                pageable(c.page(), c.size(), c.sort(), Set.of("requestedAt", "processedAt", "amount", "id"), "requestedAt"));
        List<RefundRecord> refunds = page.getContent();
        Map<Long, PaymentRecord> payments = refunds.isEmpty()
                ? Map.of()
                : paymentRepository.findAllById(refunds.stream().map(RefundRecord::getPaymentId).distinct().toList())
                        .stream().collect(Collectors.toMap(PaymentRecord::getId, Function.identity()));
        Set<Long> userIds = new HashSet<>();
        payments.values().forEach(p -> userIds.add(p.getUserId()));
        refunds.forEach(r -> userIds.add(r.getProcessedByUserId()));
        Map<Long, UserSummary> users = resolver.users(userIds);
        Map<Long, OrderBrief> orders = resolver.orders(refunds.stream().map(RefundRecord::getOrderId).toList());
        return page(page, refunds.stream()
                .map(r -> {
                    PaymentRecord payment = payments.get(r.getPaymentId());
                    return toRefund(r, payment, orderRef(lookup(orders, r.getOrderId())),
                            person(lookup(users, payment == null ? null : payment.getUserId())), users);
                })
                .toList());
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminWalletTransactionResponse> searchWalletTransactions(WalletCriteria c) {
        Page<WalletTransaction> page = walletTransactionRepository.findAll(
                all(walletUser(c.userId()),
                        walletTypeIn(c.types()),
                        walletSourceIn(c.sources()),
                        walletCreatedBetween(time.parseFrom(c.from()), time.parseToExclusive(c.to())),
                        walletReferenceLike(c.q())),
                pageable(c.page(), c.size(), c.sort(), Set.of("createdAt", "amount", "id"), "createdAt"));
        Map<Long, UserSummary> users = resolver.users(page.getContent().stream().map(WalletTransaction::getUserId).toList());
        return page(page, page.getContent().stream()
                .map(tx -> toWallet(tx, person(lookup(users, tx.getUserId()))))
                .toList());
    }

    private List<AdminPaymentResponse> toPaymentResponses(List<PaymentRecord> payments) {
        if (payments.isEmpty()) {
            return List.of();
        }
        Map<Long, BigDecimal> refunded = refundRepository
                .findByPaymentIdIn(payments.stream().map(PaymentRecord::getId).toList()).stream()
                .filter(r -> PaymentReportRules.STATUS_COMPLETED.equalsIgnoreCase(r.getStatus()))
                .collect(Collectors.groupingBy(RefundRecord::getPaymentId,
                        Collectors.reducing(BigDecimal.ZERO, r -> amount(r.getAmount()), BigDecimal::add)));
        Map<Long, UserSummary> users = resolver.users(payments.stream().map(PaymentRecord::getUserId).toList());
        Map<Long, OrderBrief> orders = resolver.orders(payments.stream().map(PaymentRecord::getOrderId).toList());
        return payments.stream()
                .map(p -> new AdminPaymentResponse(
                        p.getId(), p.getOrderId(), p.getUserId(), p.getAmount(), p.getMethod(), p.getStatus(),
                        PaymentReportRules.kind(p), p.getReferenceId(), p.getReferenceTransactionId(), p.getUrl(),
                        p.getQr(), p.getDeeplink(), p.getDescription(), p.getContent(), p.getCreatedAt(),
                        p.getUpdatedAt(), PaymentReportRules.paidAt(p), refunded.getOrDefault(p.getId(), BigDecimal.ZERO),
                        orderRef(lookup(orders, p.getOrderId())), person(lookup(users, p.getUserId()))))
                .toList();
    }

    private AdminRefundResponse toRefund(
            RefundRecord r, PaymentRecord payment, OrderRef order, PersonRef customer, Map<Long, UserSummary> users) {
        return new AdminRefundResponse(
                r.getId(), r.getPaymentId(), r.getOrderId(), r.getAmount(), r.getStatus(), r.getReason(),
                r.getTransactionId(), r.getProcessedByUserId(), r.getRequestedAt(), r.getProcessedAt(),
                payment == null ? null : payment.getUserId(),
                payment == null ? null : payment.getMethod(),
                payment == null ? null : payment.getAmount(),
                payment == null ? null : payment.getReferenceId(),
                order,
                customer,
                person(lookup(users, r.getProcessedByUserId())));
    }

    private AdminWalletTransactionResponse toWallet(WalletTransaction tx, PersonRef customer) {
        return new AdminWalletTransactionResponse(
                tx.getId(), tx.getWalletId(), tx.getUserId(), tx.getType(), tx.getAmount(), tx.getBalanceAfter(),
                tx.getSource(), tx.getReferenceId(), tx.getDescription(), tx.getCreatedAt(), relatedOrderId(tx), customer);
    }

    static Long relatedOrderId(WalletTransaction tx) {
        if (!WalletService.SOURCE_ORDER_PAYMENT.equalsIgnoreCase(tx.getSource())
                || tx.getReferenceId() == null
                || !tx.getReferenceId().startsWith(ORDER_PAYMENT_REF_PREFIX)) {
            return null;
        }
        try {
            return Long.parseLong(tx.getReferenceId().substring(ORDER_PAYMENT_REF_PREFIX.length()));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    static Pageable pageable(int page, int size, String sort, Set<String> sortable, String defaultField) {
        if (page < 0) {
            throw new BusinessException("INVALID_PAGE", "page must be >= 0");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new BusinessException("INVALID_PAGE_SIZE", "size must be between 1 and " + MAX_PAGE_SIZE);
        }
        String field = defaultField;
        Sort.Direction direction = Sort.Direction.DESC;
        if (sort != null && !sort.isBlank()) {
            String[] parts = sort.split(",");
            if (!sortable.contains(parts[0].trim())) {
                throw new BusinessException("INVALID_SORT", "sort must be one of " + sortable);
            }
            field = parts[0].trim();
            if (parts.length > 1 && "asc".equalsIgnoreCase(parts[1].trim())) {
                direction = Sort.Direction.ASC;
            }
        }
        Sort order = Sort.by(direction, field);
        if (!"id".equals(field)) {
            order = order.and(Sort.by(Sort.Direction.DESC, "id"));
        }
        return PageRequest.of(page, size, order);
    }

    private static <T> PageResponse<T> page(Page<?> page, List<T> content) {
        return new PageResponse<>(content, page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }

    private static OrderRef orderRef(OrderBrief o) {
        return o == null ? null : new OrderRef(o.id(), o.orderCode(), o.type(), o.serviceCategory(), o.status(),
                o.paymentStatus(), o.totalPrice(), o.lockerId(), o.createdAt());
    }

    private static PersonRef person(UserSummary u) {
        return u == null ? null : new PersonRef(u.id(), u.fullName(), u.phoneNumber(), u.email());
    }

    /// Map.of()/Map.copyOf() ném NPE khi get(null) — mọi tra cứu theo id đi qua đây.
    private static <T> T lookup(Map<Long, T> values, Long id) {
        return id == null ? null : values.get(id);
    }

    private static int compareDesc(java.time.LocalDateTime a, java.time.LocalDateTime b) {
        if (a == null || b == null) {
            return a == null ? (b == null ? 0 : 1) : -1;
        }
        return b.compareTo(a);
    }
}

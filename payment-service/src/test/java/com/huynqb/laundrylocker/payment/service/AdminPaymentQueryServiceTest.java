package com.huynqb.laundrylocker.payment.service;

import com.huynqb.laundrylocker.common.dto.PageResponse;
import com.huynqb.laundrylocker.common.dto.UserSummary;
import com.huynqb.laundrylocker.common.exception.BusinessException;
import com.huynqb.laundrylocker.payment.dto.admin.AdminPaymentDetailResponse;
import com.huynqb.laundrylocker.payment.dto.admin.AdminPaymentResponse;
import com.huynqb.laundrylocker.payment.dto.admin.AdminRefundResponse;
import com.huynqb.laundrylocker.payment.dto.admin.AdminWalletTransactionResponse;
import com.huynqb.laundrylocker.payment.dto.admin.OrderBrief;
import com.huynqb.laundrylocker.payment.model.PaymentRecord;
import com.huynqb.laundrylocker.payment.model.RefundRecord;
import com.huynqb.laundrylocker.payment.model.WalletTransaction;
import com.huynqb.laundrylocker.payment.repository.PaymentRepository;
import com.huynqb.laundrylocker.payment.repository.RefundRepository;
import com.huynqb.laundrylocker.payment.repository.WalletTransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminPaymentQueryServiceTest {

    @Mock private PaymentRepository paymentRepository;
    @Mock private RefundRepository refundRepository;
    @Mock private WalletTransactionRepository walletTransactionRepository;
    @Mock private PaymentReferenceResolver resolver;

    private AdminPaymentQueryService service;

    @BeforeEach
    void setUp() {
        service = new AdminPaymentQueryService(paymentRepository, refundRepository, walletTransactionRepository, resolver);
    }

    @Test
    @SuppressWarnings("unchecked")
    void searchEnrichesPageWithOrderCustomerKindAndRefundedAmount() {
        PaymentRecord order = payment(1L, 55L, "WALLET", "COMPLETED");
        PaymentRecord topup = payment(2L, 0L, "VNPAY_TOPUP", "COMPLETED");
        when(paymentRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(order, topup), PageRequest.of(0, 20), 2));
        when(refundRepository.findByPaymentIdIn(List.of(1L, 2L))).thenReturn(List.of(refund(7L, 1L, 55L, 5000)));
        when(resolver.users(anyCollection())).thenReturn(Map.of(44L, user(44L)));
        when(resolver.orders(anyCollection())).thenReturn(Map.of(55L, orderBrief(55L)));

        PageResponse<AdminPaymentResponse> page = service.searchPayments(criteria(null, null, null));

        AdminPaymentResponse first = page.content().get(0);
        assertEquals("ORDER", first.kind());
        assertEquals("ORD-55", first.order().orderCode());
        assertEquals("Nguyễn An", first.customer().fullName());
        assertEquals(0, BigDecimal.valueOf(5000).compareTo(first.refundedAmount()));
        assertEquals(LocalDateTime.of(2026, 9, 15, 2, 0), first.paidAt());
        AdminPaymentResponse second = page.content().get(1);
        assertEquals("TOPUP", second.kind());
        assertNull(second.order());
        verify(resolver, times(1)).orders(anyCollection());
    }

    @Test
    @SuppressWarnings("unchecked")
    void searchDefaultsToCreatedAtDescAndRejectsBadInput() {
        when(paymentRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        service.searchPayments(criteria(null, false, null));

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(paymentRepository).findAll(any(Specification.class), pageable.capture());
        assertEquals(Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id")),
                pageable.getValue().getSort());
        assertEquals("INVALID_KIND",
                assertThrows(BusinessException.class, () -> service.searchPayments(criteria("REFUND", null, null))).getCode());
        assertEquals("INVALID_SORT",
                assertThrows(BusinessException.class, () -> service.searchPayments(criteria(null, null, "method"))).getCode());
    }

    @Test
    void detailIncludesRefundsWalletDebitAndOtherPaymentsOfOrder() {
        PaymentRecord wallet = payment(1L, 55L, "WALLET", "COMPLETED");
        PaymentRecord failed = payment(3L, 55L, "VNPAY", "FAILED");
        WalletTransaction debit = new WalletTransaction();
        debit.setId(70L);
        debit.setUserId(44L);
        debit.setType("DEBIT");
        debit.setSource("ORDER_PAYMENT");
        debit.setReferenceId("ORDERPAY-55");
        RefundRecord refund = refund(7L, 1L, 55L, 5000);
        refund.setProcessedByUserId(1L);

        when(paymentRepository.findById(1L)).thenReturn(Optional.of(wallet));
        when(paymentRepository.findByOrderId(55L)).thenReturn(List.of(wallet, failed));
        when(refundRepository.findByPaymentIdIn(List.of(1L, 3L))).thenReturn(List.of(refund));
        when(refundRepository.findByPaymentIdIn(List.of(1L))).thenReturn(List.of(refund));
        when(walletTransactionRepository.findFirstBySourceAndReferenceId("ORDER_PAYMENT", "ORDERPAY-55"))
                .thenReturn(Optional.of(debit));
        when(resolver.users(anyCollection())).thenReturn(Map.of(44L, user(44L), 1L, user(1L)));
        when(resolver.orders(anyCollection())).thenReturn(Map.of(55L, orderBrief(55L)));

        AdminPaymentDetailResponse detail = service.paymentDetail(1L);

        assertEquals(1L, detail.payment().id());
        assertEquals(1, detail.refunds().size());
        assertEquals("ORD-55", detail.refunds().get(0).order().orderCode());
        assertEquals(1L, detail.refunds().get(0).processedBy().id());
        assertEquals(55L, detail.walletTransactions().get(0).relatedOrderId());
        assertEquals(List.of(3L), detail.orderPayments().stream().map(AdminPaymentResponse::id).toList());
    }

    @Test
    @SuppressWarnings("unchecked")
    void refundAndWalletListsResolveCustomersInOneBatch() {
        RefundRecord refund = refund(7L, 1L, 55L, 5000);
        when(refundRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(refund), PageRequest.of(0, 20), 1));
        when(paymentRepository.findAllById(List.of(1L))).thenReturn(List.of(payment(1L, 55L, "CASH", "COMPLETED")));
        when(resolver.users(anyCollection())).thenReturn(Map.of(44L, user(44L)));
        when(resolver.orders(anyCollection())).thenReturn(Map.of());

        AdminRefundResponse refundRow = service.searchRefunds(
                new AdminPaymentQueryService.RefundCriteria(0, 20, null, null, null, null, null, null, null)).content().get(0);

        assertEquals(44L, refundRow.userId());
        assertEquals("CASH", refundRow.paymentMethod());
        assertEquals("Nguyễn An", refundRow.customer().fullName());
        assertNull(refundRow.order());

        WalletTransaction topup = new WalletTransaction();
        topup.setId(80L);
        topup.setUserId(44L);
        topup.setSource("TOPUP");
        topup.setReferenceId("TOPUP_44_1");
        when(walletTransactionRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(topup), PageRequest.of(0, 20), 1));

        AdminWalletTransactionResponse walletRow = service.searchWalletTransactions(
                new AdminPaymentQueryService.WalletCriteria(0, 20, null, null, null, null, null, null, null)).content().get(0);

        assertNull(walletRow.relatedOrderId());
        assertEquals("Nguyễn An", walletRow.customer().fullName());
        assertTrue(walletRow.createdAt() == null);
    }

    private AdminPaymentQueryService.PaymentCriteria criteria(String kind, Boolean includeTopups, String sort) {
        return new AdminPaymentQueryService.PaymentCriteria(
                0, 20, null, null, kind, includeTopups, null, null, null, null, null, sort);
    }

    private PaymentRecord payment(Long id, Long orderId, String method, String status) {
        PaymentRecord payment = new PaymentRecord();
        payment.setId(id);
        payment.setOrderId(orderId);
        payment.setUserId(44L);
        payment.setMethod(method);
        payment.setStatus(status);
        payment.setAmount(BigDecimal.valueOf(15000));
        payment.setReferenceId("REF-" + id);
        payment.setCreatedAt(LocalDateTime.of(2026, 9, 15, 1, 0).plusMinutes(id));
        payment.setUpdatedAt(LocalDateTime.of(2026, 9, 15, 2, 0));
        return payment;
    }

    private RefundRecord refund(Long id, Long paymentId, Long orderId, long amount) {
        RefundRecord refund = new RefundRecord();
        refund.setId(id);
        refund.setPaymentId(paymentId);
        refund.setOrderId(orderId);
        refund.setStatus("COMPLETED");
        refund.setAmount(BigDecimal.valueOf(amount));
        return refund;
    }

    private UserSummary user(Long id) {
        return new UserSummary(id, "an@example.com", "0901234567", id == 44L ? "Nguyễn An" : "Admin", "ACTIVE");
    }

    private OrderBrief orderBrief(Long id) {
        return new OrderBrief(id, "ORD-" + id, 44L, "SEND", "PARCEL", "STORING", "PAID", null, 7L, null, null,
                BigDecimal.valueOf(15000), BigDecimal.ZERO, LocalDateTime.of(2026, 9, 15, 0, 0), null, null);
    }
}

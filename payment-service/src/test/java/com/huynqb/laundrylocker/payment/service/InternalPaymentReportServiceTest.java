package com.huynqb.laundrylocker.payment.service;

import com.huynqb.laundrylocker.common.exception.BusinessException;
import com.huynqb.laundrylocker.payment.dto.internal.CollectedPaymentsResponse;
import com.huynqb.laundrylocker.payment.dto.internal.OrderPaymentSummary;
import com.huynqb.laundrylocker.payment.model.PaymentRecord;
import com.huynqb.laundrylocker.payment.model.RefundRecord;
import com.huynqb.laundrylocker.payment.repository.PaymentRepository;
import com.huynqb.laundrylocker.payment.repository.RefundRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InternalPaymentReportServiceTest {

    @Mock private PaymentRepository paymentRepository;
    @Mock private RefundRepository refundRepository;
    @InjectMocks private InternalPaymentReportService service;

    @Test
    void orderSummaryPicksLatestAttemptAndSumsOnlyCompletedPayments() {
        PaymentRecord failedVnpay = payment(1L, 55L, "VNPAY", "FAILED", 15000, LocalDateTime.of(2026, 9, 1, 8, 0));
        PaymentRecord wallet = payment(2L, 55L, "WALLET", "COMPLETED", 15000, LocalDateTime.of(2026, 9, 1, 9, 0));
        PaymentRecord pendingExtension = payment(3L, 55L, "MOMO", "PENDING", 5000, LocalDateTime.of(2026, 9, 2, 9, 0));
        when(paymentRepository.findByOrderIdIn(List.of(55L, 56L))).thenReturn(List.of(failedVnpay, wallet, pendingExtension));
        when(refundRepository.findByOrderIdIn(List.of(55L, 56L))).thenReturn(List.of(refund(9L, 2L, 55L, "COMPLETED", 3000)));

        List<OrderPaymentSummary> result = service.orderSummaries(List.of(55L, 56L, 0L));

        assertEquals(1, result.size());
        OrderPaymentSummary summary = result.get(0);
        assertEquals(3, summary.paymentCount());
        assertEquals(3L, summary.latestPaymentId());
        assertEquals("PENDING", summary.latestStatus());
        assertEquals(0, BigDecimal.valueOf(15000).compareTo(summary.paidAmount()));
        assertEquals("WALLET", summary.lastPaidMethod());
        assertEquals(LocalDateTime.of(2026, 9, 1, 9, 0), summary.lastPaidAt());
        assertEquals(0, BigDecimal.valueOf(3000).compareTo(summary.refundedAmount()));
    }

    @Test
    void orderSummaryWithoutRealOrderIdsSkipsDatabase() {
        assertTrue(service.orderSummaries(List.of(0L)).isEmpty());
        verifyNoInteractions(paymentRepository, refundRepository);
    }

    @Test
    @SuppressWarnings("unchecked")
    void collectedReturnsPaymentsRefundsAndAllTimeOrderTotals() {
        PaymentRecord inRange = payment(10L, 77L, "WALLET", "COMPLETED", 5000, LocalDateTime.of(2026, 9, 5, 1, 0));
        PaymentRecord earlier = payment(4L, 77L, "CASH", "COMPLETED", 15000, LocalDateTime.of(2026, 8, 20, 1, 0));
        when(paymentRepository.findAll(any(Specification.class), any(Sort.class))).thenReturn(List.of(inRange));
        when(refundRepository.findAll(any(Specification.class), any(Sort.class)))
                .thenReturn(List.of(refund(3L, 4L, 77L, "COMPLETED", 2000)));
        when(paymentRepository.findAllById(List.of(4L))).thenReturn(List.of(earlier));
        when(paymentRepository.findByOrderIdIn(List.of(77L))).thenReturn(List.of(inRange, earlier));

        CollectedPaymentsResponse result = service.collected("2026-09-01T00:00", "2026-10-01T00:00", null);

        assertEquals(1, result.payments().size());
        assertEquals(LocalDateTime.of(2026, 9, 5, 1, 0), result.payments().get(0).paidAt());
        assertEquals(44L, result.refunds().get(0).userId());
        assertEquals("CASH", result.refunds().get(0).method());
        assertEquals(0, BigDecimal.valueOf(20000).compareTo(result.orderPaidTotals().get(0).paidAmount()));
    }

    @Test
    void collectedRejectsInvertedRange() {
        BusinessException error = assertThrows(BusinessException.class,
                () -> service.collected("2026-09-02T00:00", "2026-09-01T00:00", null));
        assertEquals("INVALID_DATE_RANGE", error.getCode());
    }

    private PaymentRecord payment(Long id, Long orderId, String method, String status, long amount, LocalDateTime at) {
        PaymentRecord payment = new PaymentRecord();
        payment.setId(id);
        payment.setOrderId(orderId);
        payment.setUserId(44L);
        payment.setMethod(method);
        payment.setStatus(status);
        payment.setAmount(BigDecimal.valueOf(amount));
        payment.setCreatedAt(at);
        payment.setUpdatedAt(at);
        return payment;
    }

    private RefundRecord refund(Long id, Long paymentId, Long orderId, String status, long amount) {
        RefundRecord refund = new RefundRecord();
        refund.setId(id);
        refund.setPaymentId(paymentId);
        refund.setOrderId(orderId);
        refund.setStatus(status);
        refund.setAmount(BigDecimal.valueOf(amount));
        refund.setRequestedAt(LocalDateTime.of(2026, 9, 6, 0, 0));
        refund.setProcessedAt(LocalDateTime.of(2026, 9, 6, 0, 0));
        return refund;
    }
}

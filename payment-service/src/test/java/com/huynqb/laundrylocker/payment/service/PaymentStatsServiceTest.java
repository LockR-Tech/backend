package com.huynqb.laundrylocker.payment.service;

import com.huynqb.laundrylocker.common.util.BusinessTime;
import com.huynqb.laundrylocker.payment.dto.admin.PaymentStatsResponse;
import com.huynqb.laundrylocker.payment.model.PaymentRecord;
import com.huynqb.laundrylocker.payment.model.RefundRecord;
import com.huynqb.laundrylocker.payment.repository.PaymentRepository;
import com.huynqb.laundrylocker.payment.repository.RefundRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentStatsServiceTest {

    @Mock private PaymentRepository paymentRepository;
    @Mock private RefundRepository refundRepository;

    private PaymentStatsService service;

    @BeforeEach
    void setUp() {
        service = new PaymentStatsService(paymentRepository, refundRepository);
        // Bây giờ: 2026-09-15 10:00 giờ VN (03:00 UTC).
        service.setTime(new BusinessTime(ZoneOffset.UTC, BusinessTime.DEFAULT_BUSINESS_ZONE,
                Clock.fixed(Instant.parse("2026-09-15T03:00:00Z"), ZoneOffset.UTC)));
    }

    @Test
    @SuppressWarnings("unchecked")
    void bucketsByVietnamDayAndComparesWithPreviousPeriodAndYesterday() {
        List<PaymentRecord> payments = List.of(
                // Hôm nay (VN): 2026-09-15 08:00 VN = 01:00 UTC
                payment(1L, 10L, "WALLET", "COMPLETED", 15000, LocalDateTime.of(2026, 9, 15, 1, 0)),
                payment(2L, 0L, "VNPAY_TOPUP", "COMPLETED", 50000, LocalDateTime.of(2026, 9, 15, 2, 0)),
                // 2026-09-14 23:30 UTC đã là 2026-09-15 06:30 VN ⇒ hôm nay
                payment(3L, 11L, "VNPAY", "FAILED", 20000, LocalDateTime.of(2026, 9, 14, 23, 30)),
                // Hôm qua (VN)
                payment(4L, 12L, "CASH", "COMPLETED", 10000, LocalDateTime.of(2026, 9, 14, 5, 0)),
                // Kỳ này 14..15 ⇒ kỳ trước 12..13
                payment(5L, 13L, "MOMO", "COMPLETED", 30000, LocalDateTime.of(2026, 9, 12, 5, 0)));
        RefundRecord refund = new RefundRecord();
        refund.setPaymentId(1L);
        refund.setOrderId(10L);
        refund.setStatus("COMPLETED");
        refund.setAmount(BigDecimal.valueOf(5000));
        refund.setRequestedAt(LocalDateTime.of(2026, 9, 15, 2, 30));
        when(paymentRepository.findAll(any(Specification.class))).thenReturn(payments);
        when(refundRepository.findAll(any(Specification.class))).thenReturn(List.of(refund));

        PaymentStatsResponse stats = service.stats("2026-09-14", "2026-09-15");

        assertEquals(LocalDate.of(2026, 9, 12), stats.previousFrom());
        assertEquals(LocalDate.of(2026, 9, 13), stats.previousTo());

        PaymentStatsResponse.PeriodStats today = stats.today();
        assertEquals(3, today.totalCount());
        assertEquals(2, today.completedCount());
        assertEquals(1, today.failedCount());
        assertEquals(0, BigDecimal.valueOf(15000).compareTo(today.orderPayments().completedAmount()));
        assertEquals(0, BigDecimal.valueOf(50000).compareTo(today.topups().completedAmount()));
        assertEquals(0, BigDecimal.valueOf(10000).compareTo(today.netCollectedAmount()));
        assertEquals(new BigDecimal("66.67"), today.successRate());

        assertEquals(1, stats.yesterday().totalCount());
        assertEquals(4, stats.current().totalCount());
        assertEquals(1, stats.previous().totalCount());
        // Kỳ này thu đơn 25000 − hoàn 5000 = 20000; kỳ trước 30000 ⇒ −33.33%
        assertEquals(new BigDecimal("-33.33"), stats.changes().netCollectedAmountPct());
        assertNull(stats.changes().refundAmountPct());
        assertEquals("VNPAY_TOPUP", stats.current().byMethod().get(0).key());
    }

    @Test
    void pctReturnsNullWhenBaselineIsZero() {
        assertNull(PaymentStatsService.pct(BigDecimal.TEN, BigDecimal.ZERO));
        assertEquals(new BigDecimal("100.00"), PaymentStatsService.pct(BigDecimal.valueOf(20), BigDecimal.TEN));
    }

    private PaymentRecord payment(Long id, Long orderId, String method, String status, long amount, LocalDateTime createdAt) {
        PaymentRecord payment = new PaymentRecord();
        payment.setId(id);
        payment.setOrderId(orderId);
        payment.setUserId(44L);
        payment.setMethod(method);
        payment.setStatus(status);
        payment.setAmount(BigDecimal.valueOf(amount));
        payment.setCreatedAt(createdAt);
        payment.setUpdatedAt(createdAt);
        return payment;
    }
}

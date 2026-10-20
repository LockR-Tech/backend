package com.huynqb.laundrylocker.loyalty.service;

import com.huynqb.laundrylocker.common.event.DomainEvent;
import com.huynqb.laundrylocker.common.event.DomainEventNames;
import com.huynqb.laundrylocker.loyalty.model.LoyaltyAccount;
import com.huynqb.laundrylocker.loyalty.model.PointTransaction;
import com.huynqb.laundrylocker.loyalty.repository.LoyaltyAccountRepository;
import com.huynqb.laundrylocker.loyalty.repository.PointTransactionRepository;
import com.huynqb.laundrylocker.loyalty.settings.LoyaltyRules;
import com.huynqb.laundrylocker.loyalty.settings.TestLoyaltyRules;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/// Đơn được hoàn tiền ⇒ thu hồi điểm đã tích theo tỉ lệ tiền đã hoàn.
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LoyaltyEarnReversalTest {

    @Mock LoyaltyAccountRepository accountRepository;
    @Mock PointTransactionRepository transactionRepository;

    private LoyaltyService service;
    private LoyaltyAccount account;

    @BeforeEach
    void setUp() {
        service = new LoyaltyService(accountRepository, transactionRepository,
                new LoyaltyRules(TestLoyaltyRules.settings(Map.of())));
        account = new LoyaltyAccount();
        account.setUserId(7L);
        account.setPoints(100);
        account.setLifetimePoints(100);
        when(accountRepository.findByUserIdForUpdate(7L)).thenReturn(Optional.of(account));
        when(accountRepository.save(any(LoyaltyAccount.class))).thenAnswer(inv -> inv.getArgument(0));
        PointTransaction earn = new PointTransaction();
        earn.setUserId(7L);
        earn.setOrderId(55L);
        earn.setPoints(20);
        earn.setType(LoyaltyService.ORDER_EARN_TYPE);
        when(transactionRepository.findFirstByOrderIdAndType(55L, LoyaltyService.ORDER_EARN_TYPE))
                .thenReturn(Optional.of(earn));
    }

    @Test
    void fullRefundTakesBackAllEarnedPointsAndLowersLifetimeTotal() {
        assertEquals(20, service.reverseOrderEarn(55L, new BigDecimal("20000"), new BigDecimal("20000")));

        assertEquals(80, account.getPoints());
        assertEquals(80, account.getLifetimePoints());
        ArgumentCaptor<PointTransaction> saved = ArgumentCaptor.forClass(PointTransaction.class);
        verify(transactionRepository).saveAndFlush(saved.capture());
        assertEquals(-20, saved.getValue().getPoints());
        assertEquals(LoyaltyService.ORDER_EARN_REVERSAL_TYPE, saved.getValue().getType());
    }

    @Test
    void partialRefundTakesBackOnlyItsShareAndRepeatsOnlyTheDifference() {
        // Hoàn 5.000 / 20.000 ⇒ 5 điểm.
        assertEquals(5, service.reverseOrderEarn(55L, new BigDecimal("5000"), new BigDecimal("20000")));
        // Sự kiện giao lại cùng tổng đã hoàn: không thu thêm.
        when(transactionRepository.sumPointsByOrderIdAndType(55L, LoyaltyService.ORDER_EARN_REVERSAL_TYPE))
                .thenReturn(-5L);
        assertEquals(0, service.reverseOrderEarn(55L, new BigDecimal("5000"), new BigDecimal("20000")));
        // Hoàn thêm tới đủ: chỉ thu phần còn lại.
        assertEquals(15, service.reverseOrderEarn(55L, new BigDecimal("20000"), new BigDecimal("20000")));
        assertEquals(80, account.getPoints());
    }

    @Test
    void neverTakesMoreThanTheCurrentBalance() {
        account.setPoints(3);

        assertEquals(3, service.reverseOrderEarn(55L, new BigDecimal("20000"), new BigDecimal("20000")));
        assertEquals(0, account.getPoints());
    }

    @Test
    void orderThatEarnedNothingLosesNothing() {
        when(transactionRepository.findFirstByOrderIdAndType(56L, LoyaltyService.ORDER_EARN_TYPE))
                .thenReturn(Optional.empty());

        assertEquals(0, service.reverseOrderEarn(56L, new BigDecimal("20000"), new BigDecimal("20000")));
        verify(transactionRepository, never()).saveAndFlush(any());
    }

    @Test
    void refundEventFromPaymentServiceReachesTheReversal() {
        LoyaltyService loyalty = org.mockito.Mockito.mock(LoyaltyService.class);
        new OrderCompletedPointsListener(loyalty).onOrderCompleted(DomainEvent.of(
                DomainEventNames.PAYMENT_REFUNDED, "payment-service",
                Map.of("orderId", 55L, "refundedAmount", new BigDecimal("5000"), "paidAmount", new BigDecimal("20000"))));

        verify(loyalty).reverseOrderEarn(55L, new BigDecimal("5000"), new BigDecimal("20000"));
        verify(loyalty, never()).awardOrderPoints(any(), any(), any());
    }
}

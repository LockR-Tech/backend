package com.huynqb.laundrylocker.loyalty.service;

import com.huynqb.laundrylocker.common.exception.BusinessException;
import com.huynqb.laundrylocker.loyalty.dto.AdjustPointsRequest;
import com.huynqb.laundrylocker.loyalty.dto.VoucherExchangeRequest;
import com.huynqb.laundrylocker.loyalty.dto.VoucherExchangeResponse;
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

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LoyaltyVoucherExchangeTest {

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
        account.setPoints(200_000);
        account.setLifetimePoints(200_000);
        when(accountRepository.findByUserId(7L)).thenReturn(Optional.of(account));
        when(accountRepository.findByUserIdForUpdate(7L)).thenReturn(Optional.of(account));
        when(accountRepository.save(any(LoyaltyAccount.class))).thenAnswer(inv -> inv.getArgument(0));
        when(transactionRepository.findFirstByReferenceAndType(any(), any())).thenReturn(Optional.empty());
    }

    @Test
    void exchangeDeductsPointsWithoutLoweringTier() {
        VoucherExchangeResponse result = service.exchangeForVoucher(new VoucherExchangeRequest(7L, 10_000, "VX-1"));

        assertTrue(result.exchanged());
        assertEquals(10_000, result.pointsSpent());
        assertEquals(190_000, result.balance());
        // Đổi voucher là tiêu điểm: tổng điểm đã tích (hạng) giữ nguyên.
        assertEquals(200_000, account.getLifetimePoints());
        ArgumentCaptor<PointTransaction> saved = ArgumentCaptor.forClass(PointTransaction.class);
        verify(transactionRepository).saveAndFlush(saved.capture());
        assertEquals(-10_000, saved.getValue().getPoints());
        assertEquals(LoyaltyService.VOUCHER_EXCHANGE_TYPE, saved.getValue().getType());
        assertEquals("VX-1", saved.getValue().getReference());
    }

    @Test
    void notEnoughPointsExchangesNothing() {
        account.setPoints(9_999);

        VoucherExchangeResponse result = service.exchangeForVoucher(new VoucherExchangeRequest(7L, 10_000, "VX-2"));

        assertFalse(result.exchanged());
        assertEquals(9_999, result.balance());
        assertEquals(9_999, account.getPoints());
        verify(transactionRepository, never()).saveAndFlush(any());
    }

    @Test
    void sameReferenceIsDeductedOnce() {
        PointTransaction first = new PointTransaction();
        first.setUserId(7L);
        first.setPoints(-10_000);
        first.setType(LoyaltyService.VOUCHER_EXCHANGE_TYPE);
        first.setReference("VX-3");
        when(transactionRepository.findFirstByReferenceAndType("VX-3", LoyaltyService.VOUCHER_EXCHANGE_TYPE))
                .thenReturn(Optional.of(first));

        VoucherExchangeResponse again = service.exchangeForVoucher(new VoucherExchangeRequest(7L, 10_000, "VX-3"));

        assertTrue(again.exchanged());
        assertEquals(10_000, again.pointsSpent());
        assertEquals(200_000, account.getPoints());
        verify(transactionRepository, never()).saveAndFlush(any());
    }

    @Test
    void refundGivesPointsBackOnce() {
        PointTransaction exchange = new PointTransaction();
        exchange.setUserId(7L);
        exchange.setPoints(-10_000);
        exchange.setType(LoyaltyService.VOUCHER_EXCHANGE_TYPE);
        exchange.setReference("VX-4");
        account.setPoints(190_000);
        when(transactionRepository.findFirstByReferenceAndType("VX-4", LoyaltyService.VOUCHER_EXCHANGE_TYPE))
                .thenReturn(Optional.of(exchange));
        when(transactionRepository.existsByReferenceAndType("VX-4", LoyaltyService.VOUCHER_EXCHANGE_REFUND_TYPE))
                .thenReturn(false, false, true); // kiểm trước và dưới khoá, rồi lần gọi thứ hai

        assertEquals(10_000, service.refundVoucherExchange("VX-4").pointsRefunded());
        assertEquals(200_000, account.getPoints());
        assertEquals(0, service.refundVoucherExchange("VX-4").pointsRefunded());
        assertEquals(0, service.refundVoucherExchange("VX-unknown").pointsRefunded());
        verify(transactionRepository, times(1)).saveAndFlush(any());
    }

    @Test
    void adminCannotWriteVoucherExchangeRowsByHand() {
        assertThrows(BusinessException.class, () -> service.adjustPoints(
                new AdjustPointsRequest(7L, null, 10_000, LoyaltyService.VOUCHER_EXCHANGE_REFUND_TYPE)));
    }
}

package com.huynqb.laundrylocker.loyalty.service;

import com.huynqb.laundrylocker.common.exception.BusinessException;
import com.huynqb.laundrylocker.common.settings.BusinessSettings;
import com.huynqb.laundrylocker.loyalty.dto.AdjustPointsRequest;
import com.huynqb.laundrylocker.loyalty.dto.LoyaltyAccountResponse;
import com.huynqb.laundrylocker.loyalty.dto.RedeemPointsRequest;
import com.huynqb.laundrylocker.loyalty.dto.RedeemStampRequest;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/// Điểm tự cộng khi đơn hoàn tất (mỗi đơn một lần, theo số tiền thực trả) và giới hạn khi đổi điểm/tem.
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LoyaltyOrderPointsTest {

    @Mock LoyaltyAccountRepository accountRepository;
    @Mock PointTransactionRepository transactionRepository;

    private BusinessSettings settings;
    private LoyaltyService service;
    private LoyaltyAccount account;

    @BeforeEach
    void setUp() {
        settings = TestLoyaltyRules.settings(Map.of());
        service = new LoyaltyService(accountRepository, transactionRepository, new LoyaltyRules(settings));
        account = new LoyaltyAccount();
        account.setUserId(7L);
        account.setPoints(100);
        when(accountRepository.findByUserId(7L)).thenReturn(Optional.of(account));
        when(accountRepository.findByUserIdForUpdate(7L)).thenReturn(Optional.of(account));
        when(accountRepository.save(any(LoyaltyAccount.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void completedOrderEarnsTenPercentAsPoints() {
        // Mặc định 1 điểm mỗi 10đ đã trả, 1 điểm = 1đ khi dùng ⇒ tích 10% số tiền.
        Optional<LoyaltyAccountResponse> result = service.awardOrderPoints(55L, 7L, new BigDecimal("25505"));

        assertTrue(result.isPresent());
        assertEquals(2650, result.get().points());
        ArgumentCaptor<PointTransaction> saved = ArgumentCaptor.forClass(PointTransaction.class);
        verify(transactionRepository).saveAndFlush(saved.capture());
        assertEquals(2550, saved.getValue().getPoints());
        assertEquals(55L, saved.getValue().getOrderId());
        assertEquals(LoyaltyService.ORDER_EARN_TYPE, saved.getValue().getType());
    }

    @Test
    void earnRateFollowsAdminSetting() {
        settings.update(Map.of("app.loyalty.vnd-per-point", 500), null);

        assertEquals(151, service.awardOrderPoints(55L, 7L, new BigDecimal("25500")).orElseThrow().points());
    }

    @Test
    void freeOrTinyOrdersEarnNothing() {
        assertTrue(service.awardOrderPoints(55L, 7L, BigDecimal.ZERO).isEmpty());
        assertTrue(service.awardOrderPoints(56L, 7L, new BigDecimal("9")).isEmpty());
        assertTrue(service.awardOrderPoints(57L, 7L, null).isEmpty());
        verify(transactionRepository, never()).saveAndFlush(any());
        assertEquals(100, account.getPoints());
    }

    @Test
    void orderAlreadyRewardedIsNotRewardedAgain() {
        when(transactionRepository.existsByOrderIdAndType(55L, LoyaltyService.ORDER_EARN_TYPE)).thenReturn(true);

        assertTrue(service.awardOrderPoints(55L, 7L, new BigDecimal("50000")).isEmpty());
        verify(transactionRepository, never()).saveAndFlush(any());
        assertEquals(100, account.getPoints());
    }

    @Test
    void manualAdjustmentCannotUseOrderEarnType() {
        BusinessException error = assertThrows(BusinessException.class,
                () -> service.adjustPoints(new AdjustPointsRequest(7L, 55L, 10, "order_earn")));

        assertEquals("LOYALTY_TYPE_RESERVED", error.getCode());
    }

    @Test
    void redeemingMoreThanBalanceIsRejected() {
        assertEquals("LOYALTY_POINTS_INSUFFICIENT", assertThrows(BusinessException.class,
                () -> service.redeemPoints(new RedeemPointsRequest(7L, 101, null))).getCode());
        assertEquals("LOYALTY_POINTS_INVALID", assertThrows(BusinessException.class,
                () -> service.redeemPoints(new RedeemPointsRequest(7L, 0, null))).getCode());
        assertEquals(100, account.getPoints());

        assertEquals(40, service.redeemPoints(new RedeemPointsRequest(7L, 60, null)).points());
    }

    @Test
    void redeemingMoreStampsThanOwnedIsRejected() {
        account.setStamps(3);

        assertEquals("LOYALTY_STAMPS_INSUFFICIENT", assertThrows(BusinessException.class,
                () -> service.redeemStamp(new RedeemStampRequest(7L, 4, null))).getCode());
        assertEquals(3, account.getStamps());
        assertEquals(0, service.redeemStamp(new RedeemStampRequest(7L, 3, null)).stamps());
    }
}

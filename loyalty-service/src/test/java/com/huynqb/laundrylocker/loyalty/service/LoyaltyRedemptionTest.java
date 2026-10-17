package com.huynqb.laundrylocker.loyalty.service;

import com.huynqb.laundrylocker.common.exception.BusinessException;
import com.huynqb.laundrylocker.common.settings.BusinessSettings;
import com.huynqb.laundrylocker.loyalty.dto.AdjustPointsRequest;
import com.huynqb.laundrylocker.loyalty.dto.LoyaltyAccountResponse;
import com.huynqb.laundrylocker.loyalty.dto.OrderRedemptionRequest;
import com.huynqb.laundrylocker.loyalty.dto.OrderRedemptionResponse;
import com.huynqb.laundrylocker.loyalty.dto.RedeemPreviewResponse;
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
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/// Dùng điểm thanh toán đơn: kẹp theo quy tắc admin, idempotent theo orderId, hoàn đúng một lần, và hạng
/// theo tổng điểm đã tích (tiêu điểm không tụt hạng).
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LoyaltyRedemptionTest {

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
        account.setId(1L);
        account.setUserId(7L);
        account.setPoints(500);
        account.setLifetimePoints(600);
        when(accountRepository.findByUserId(7L)).thenReturn(Optional.of(account));
        when(accountRepository.findByUserIdForUpdate(7L)).thenReturn(Optional.of(account));
        when(accountRepository.save(any(LoyaltyAccount.class))).thenAnswer(inv -> inv.getArgument(0));
        when(transactionRepository.findFirstByOrderIdAndType(any(), any())).thenReturn(Optional.empty());
    }

    @Test
    void previewCapsByPercentOfOrderAndBalance() {
        // 15 000 đ × 50% / 100 đ = 75 điểm (số dư 500 đủ).
        RedeemPreviewResponse preview = service.redeemPreview(7L, new BigDecimal("15000"));
        assertEquals(true, preview.enabled());
        assertEquals(500, preview.balance());
        assertEquals(100, preview.vndPerPoint());
        assertEquals(50, preview.maxPercent());
        assertEquals(10, preview.minPoints());
        assertEquals(75, preview.maxPoints());
        assertEquals(new BigDecimal("7500"), preview.maxDiscount());

        // Số dư thấp hơn trần theo đơn ⇒ dùng hết số dư.
        account.setPoints(60);
        assertEquals(60, service.redeemPreview(7L, new BigDecimal("15000")).maxPoints());
    }

    @Test
    void previewIsZeroBelowMinimumOrWhenDisabled() {
        account.setPoints(9);
        RedeemPreviewResponse belowMin = service.redeemPreview(7L, new BigDecimal("15000"));
        assertEquals(0, belowMin.maxPoints());
        assertEquals(0, belowMin.maxDiscount().signum());

        account.setPoints(500);
        // Đơn 1 500 đ × 50% = 750 đ = 7 điểm < tối thiểu 10.
        assertEquals(0, service.redeemPreview(7L, new BigDecimal("1500")).maxPoints());

        settings.update(Map.of("app.loyalty.redeem-enabled", false), null);
        RedeemPreviewResponse disabled = service.redeemPreview(7L, new BigDecimal("15000"));
        assertFalse(disabled.enabled());
        assertEquals(0, disabled.maxPoints());
    }

    @Test
    void previewFollowsAdminRedeemSettings() {
        settings.update(Map.of(
                "app.loyalty.redeem-vnd-per-point", 50,
                "app.loyalty.redeem-max-percent", 100,
                "app.loyalty.redeem-min-points", 0), null);

        // 15 000 đ × 100% / 50 đ = 300 điểm.
        RedeemPreviewResponse preview = service.redeemPreview(7L, new BigDecimal("15000"));
        assertEquals(300, preview.maxPoints());
        assertEquals(new BigDecimal("15000"), preview.maxDiscount());
    }

    @Test
    void redeemClampsRequestAndWritesNegativeOrderRedeem() {
        OrderRedemptionResponse result =
                service.redeemForOrder(new OrderRedemptionRequest(7L, 55L, new BigDecimal("15000"), 1000));

        assertEquals(75, result.pointsUsed());
        assertEquals(new BigDecimal("7500"), result.discountAmount());
        assertEquals(425, account.getPoints());
        // Dùng điểm không đổi tổng điểm đã tích nên không tụt hạng.
        assertEquals(600, account.getLifetimePoints());
        assertEquals("SILVER", account.getTier());

        ArgumentCaptor<PointTransaction> saved = ArgumentCaptor.forClass(PointTransaction.class);
        verify(transactionRepository).saveAndFlush(saved.capture());
        assertEquals(-75, saved.getValue().getPoints());
        assertEquals(55L, saved.getValue().getOrderId());
        assertEquals(LoyaltyService.ORDER_REDEEM_TYPE, saved.getValue().getType());
        assertEquals(new BigDecimal("7500"), saved.getValue().getAmount());
    }

    @Test
    void redeemNothingForZeroRequestBelowMinimumOrMissingAccount() {
        assertEquals(0, service.redeemForOrder(new OrderRedemptionRequest(7L, 55L, new BigDecimal("15000"), 0)).pointsUsed());
        assertEquals(0, service.redeemForOrder(new OrderRedemptionRequest(7L, 55L, new BigDecimal("15000"), null)).pointsUsed());
        // Xin 5 điểm < tối thiểu 10.
        assertEquals(0, service.redeemForOrder(new OrderRedemptionRequest(7L, 55L, new BigDecimal("15000"), 5)).pointsUsed());
        when(accountRepository.findByUserIdForUpdate(8L)).thenReturn(Optional.empty());
        assertEquals(0, service.redeemForOrder(new OrderRedemptionRequest(8L, 56L, new BigDecimal("15000"), 50)).pointsUsed());

        verify(transactionRepository, never()).saveAndFlush(any());
        assertEquals(500, account.getPoints());
    }

    @Test
    void redeemIsIdempotentPerOrder() {
        PointTransaction earlier = transaction(55L, -40, LoyaltyService.ORDER_REDEEM_TYPE, new BigDecimal("4000"));
        when(transactionRepository.findFirstByOrderIdAndType(55L, LoyaltyService.ORDER_REDEEM_TYPE))
                .thenReturn(Optional.of(earlier));

        // Admin đã đổi giá trị điểm sau lần trừ đầu: lần gọi lại vẫn trả đúng số tiền cũ.
        settings.update(Map.of("app.loyalty.redeem-vnd-per-point", 200), null);
        OrderRedemptionResponse again =
                service.redeemForOrder(new OrderRedemptionRequest(7L, 55L, new BigDecimal("15000"), 75));

        assertEquals(40, again.pointsUsed());
        assertEquals(new BigDecimal("4000"), again.discountAmount());
        verify(transactionRepository, never()).saveAndFlush(any());
        assertEquals(500, account.getPoints());
    }

    @Test
    void redeemForAnotherUsersOrderIsRejected() {
        PointTransaction earlier = transaction(55L, -40, LoyaltyService.ORDER_REDEEM_TYPE, new BigDecimal("4000"));
        earlier.setUserId(99L);
        when(transactionRepository.findFirstByOrderIdAndType(55L, LoyaltyService.ORDER_REDEEM_TYPE))
                .thenReturn(Optional.of(earlier));

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.redeemForOrder(new OrderRedemptionRequest(7L, 55L, new BigDecimal("15000"), 75)));
        assertEquals("LOYALTY_REDEMPTION_CONFLICT", error.getCode());
        assertEquals(HttpStatus.CONFLICT, error.getStatus());
    }

    @Test
    void refundReturnsExactlyTheRedeemedPointsOnce() {
        PointTransaction redemption = transaction(55L, -75, LoyaltyService.ORDER_REDEEM_TYPE, new BigDecimal("7500"));
        when(transactionRepository.findFirstByOrderIdAndType(55L, LoyaltyService.ORDER_REDEEM_TYPE))
                .thenReturn(Optional.of(redemption));
        account.setPoints(425);

        assertEquals(75, service.refundOrderRedemption(55L).pointsRefunded());
        assertEquals(500, account.getPoints());
        assertEquals(600, account.getLifetimePoints());
        ArgumentCaptor<PointTransaction> saved = ArgumentCaptor.forClass(PointTransaction.class);
        verify(transactionRepository).saveAndFlush(saved.capture());
        assertEquals(75, saved.getValue().getPoints());
        assertEquals(LoyaltyService.ORDER_REDEEM_REFUND_TYPE, saved.getValue().getType());
        assertEquals(new BigDecimal("7500"), saved.getValue().getAmount());

        // Lần hoàn thứ hai: đã có ORDER_REDEEM_REFUND ⇒ 0, không ghi thêm.
        when(transactionRepository.existsByOrderIdAndType(55L, LoyaltyService.ORDER_REDEEM_REFUND_TYPE))
                .thenReturn(true);
        assertEquals(0, service.refundOrderRedemption(55L).pointsRefunded());
        assertEquals(500, account.getPoints());
    }

    @Test
    void refundOfOrderWithoutRedemptionIsZero() {
        assertEquals(0, service.refundOrderRedemption(77L).pointsRefunded());
        assertEquals(0, service.refundOrderRedemption(null).pointsRefunded());
        verify(transactionRepository, never()).saveAndFlush(any());
    }

    @Test
    void tierFollowsLifetimePointsNotBalance() {
        account.setPoints(0);
        account.setLifetimePoints(0);

        LoyaltyAccountResponse earned = service.awardOrderPoints(60L, 7L, new BigDecimal("600000")).orElseThrow();
        assertEquals(600, earned.points());
        assertEquals(600, earned.lifetimePoints());
        assertEquals("SILVER", earned.tier());

        // Tiêu hết điểm: số dư về 0 nhưng vẫn SILVER.
        OrderRedemptionResponse redeemed = service.redeemForOrder(
                new OrderRedemptionRequest(7L, 61L, new BigDecimal("2000000"), 600));
        assertEquals(600, redeemed.pointsUsed());
        assertEquals(0, account.getPoints());
        assertEquals("SILVER", service.getByUser(7L).tier());
        assertEquals(600, service.getByUser(7L).lifetimePoints());
    }

    @Test
    void manualAdjustmentMovesLifetimeButNeverBelowZero() {
        assertEquals(700, service.adjustPoints(new AdjustPointsRequest(7L, null, 100, null)).lifetimePoints());
        LoyaltyAccountResponse negative = service.adjustPoints(new AdjustPointsRequest(7L, null, -1000, "ADJUSTMENT"));
        assertEquals(-400, negative.points());
        assertEquals(0, negative.lifetimePoints());
        assertEquals("BRONZE", negative.tier());
    }

    @Test
    void adminCannotWriteSystemRedemptionTypes() {
        assertEquals("LOYALTY_TYPE_RESERVED", assertThrows(BusinessException.class,
                () -> service.adjustPoints(new AdjustPointsRequest(7L, 55L, -10, "order_redeem"))).getCode());
        assertEquals("LOYALTY_TYPE_RESERVED", assertThrows(BusinessException.class,
                () -> service.adjustPoints(new AdjustPointsRequest(7L, 55L, 10, "ORDER_REDEEM_REFUND"))).getCode());
        verify(transactionRepository, never()).saveAndFlush(any());
    }

    private static PointTransaction transaction(Long orderId, int points, String type, BigDecimal amount) {
        PointTransaction transaction = new PointTransaction();
        transaction.setUserId(7L);
        transaction.setOrderId(orderId);
        transaction.setPoints(points);
        transaction.setType(type);
        transaction.setAmount(amount);
        return transaction;
    }
}

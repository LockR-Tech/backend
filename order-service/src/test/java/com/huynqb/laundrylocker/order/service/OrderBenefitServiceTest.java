package com.huynqb.laundrylocker.order.service;

import com.huynqb.laundrylocker.common.dto.ApiResponse;
import com.huynqb.laundrylocker.common.exception.BusinessException;
import com.huynqb.laundrylocker.order.client.LockerLookupClient;
import com.huynqb.laundrylocker.order.client.LoyaltyClient;
import com.huynqb.laundrylocker.order.dto.LoyaltyRedemption;
import com.huynqb.laundrylocker.order.dto.admin.LockerInfo;
import com.huynqb.laundrylocker.order.model.LockerOrder;
import com.huynqb.laundrylocker.order.model.Promotion;
import com.huynqb.laundrylocker.order.model.PromotionClaim;
import com.huynqb.laundrylocker.order.model.PromotionUsage;
import com.huynqb.laundrylocker.order.repository.LockerOrderRepository;
import com.huynqb.laundrylocker.order.repository.PromotionClaimRepository;
import com.huynqb.laundrylocker.order.repository.PromotionRepository;
import com.huynqb.laundrylocker.order.repository.PromotionUsageRepository;
import com.huynqb.laundrylocker.order.settings.TestOrderRules;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/// Giá đơn với mã giảm giá + điểm thưởng, mã theo tủ, số mã tối đa mỗi đơn, trần lượt dưới khoá, và trả
/// ưu đãi khi huỷ/hoàn tiền.
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OrderBenefitServiceTest {

    @Mock PromotionRepository promotionRepository;
    @Mock PromotionUsageRepository promotionUsageRepository;
    @Mock PromotionClaimRepository promotionClaimRepository;
    @Mock LockerOrderRepository orderRepository;
    @Mock LoyaltyClient loyaltyClient;
    @Mock LockerLookupClient lockerLookupClient;

    private OrderBenefitService benefits;

    @BeforeEach
    void setUp() {
        benefits = service(Map.of());
        when(promotionRepository.save(any(Promotion.class))).thenAnswer(inv -> inv.getArgument(0));
        when(lockerLookupClient.getLockers(any())).thenReturn(ApiResponse.ok(List.of(locker(7L, "TU07", "Tủ Thư viện"))));
    }

    private OrderBenefitService service(Map<String, ?> settings) {
        return new OrderBenefitService(
                promotionRepository, promotionUsageRepository, promotionClaimRepository, orderRepository,
                loyaltyClient, new PromotionViews(lockerLookupClient), TestOrderRules.of(settings));
    }

    @Test
    void pricingAppliesVoucherFirstThenPointsOnTheRemainder() {
        LockerOrder order = order(20000, 5L);
        Promotion voucher = promotion(1L, "GIAM5K", "5000", null);
        when(promotionRepository.findByCodeForUpdate("GIAM5K")).thenReturn(Optional.of(voucher));
        when(loyaltyClient.redeem(any())).thenReturn(ApiResponse.ok(new LoyaltyRedemption.Result(50, new BigDecimal("5000"))));
        PromotionClaim claim = new PromotionClaim();
        claim.setStatus("SAVED");
        when(promotionClaimRepository.findByPromotionIdAndUserId(1L, 44L)).thenReturn(Optional.of(claim));

        benefits.applyVouchers(order, OrderBenefitService.normalizeCodes(" giam5k ", List.of("GIAM5K")), 5L);
        benefits.redeemPoints(order, 80);

        assertEquals(0, new BigDecimal("5000").compareTo(order.getDiscount()));
        assertEquals("GIAM5K", order.getPromotionCode());
        assertEquals(50, order.getPointsUsed());
        assertEquals(0, new BigDecimal("5000").compareTo(order.getPointsDiscount()));
        assertEquals(0, new BigDecimal("10000").compareTo(order.getTotalPrice()));
        // Điểm tính trên số tiền SAU mã giảm giá.
        verify(loyaltyClient).redeem(new LoyaltyRedemption.Request(44L, 21L, new BigDecimal("15000"), 80));
        // Mã nhập hai lần chỉ dùng một lượt.
        assertEquals(1, voucher.getUsageCount());
        ArgumentCaptor<PromotionUsage> usage = ArgumentCaptor.forClass(PromotionUsage.class);
        verify(promotionUsageRepository).save(usage.capture());
        assertEquals(0, new BigDecimal("5000").compareTo(usage.getValue().getDiscountApplied()));
        assertEquals(21L, usage.getValue().getOrderId());
        assertEquals("USED", claim.getStatus());
    }

    @Test
    void pointsDiscountNeverExceedsWhatIsLeftAndTotalNeverGoesNegative() {
        LockerOrder order = order(15000, 5L);
        when(loyaltyClient.redeem(any())).thenReturn(ApiResponse.ok(new LoyaltyRedemption.Result(500, new BigDecimal("50000"))));

        benefits.redeemPoints(order, 500);

        assertEquals(0, new BigDecimal("15000").compareTo(order.getPointsDiscount()));
        assertEquals(0, BigDecimal.ZERO.compareTo(order.getTotalPrice()));
    }

    @Test
    void noPointsRequestedOrNothingLeftToPayDoesNotCallLoyalty() {
        LockerOrder order = order(15000, 5L);
        benefits.redeemPoints(order, null);
        benefits.redeemPoints(order, 0);
        order.setDiscount(new BigDecimal("15000"));
        benefits.redeemPoints(order, 100);

        verify(loyaltyClient, never()).redeem(any());
        assertEquals(0, order.getPointsUsed());
    }

    @Test
    void loyaltyOutageCreatesTheOrderWithoutPointsAndCompensates() {
        LockerOrder order = order(15000, 5L);
        when(loyaltyClient.redeem(any())).thenThrow(new RuntimeException("loyalty-service down"));

        benefits.redeemPoints(order, 100);

        assertEquals(0, order.getPointsUsed());
        assertEquals(0, BigDecimal.ZERO.compareTo(order.getPointsDiscount()));
        assertEquals(0, new BigDecimal("15000").compareTo(order.getTotalPrice()));
        // Lời gọi có thể đã trừ dù phản hồi lỗi: hoàn bù (idempotent bên loyalty).
        verify(loyaltyClient).refund(21L);
    }

    @Test
    void lockerScopedVoucherIsRejectedWithoutTheMatchingLocker() {
        Promotion scoped = promotion(2L, "TUTV", "3000", 7L);
        when(promotionRepository.findByCodeForUpdate("TUTV")).thenReturn(Optional.of(scoped));

        BusinessException noLocker = assertThrows(BusinessException.class,
                () -> benefits.applyVouchers(order(15000, null), List.of("TUTV"), null));
        assertEquals("PROMOTION_INVALID", noLocker.getCode());
        assertTrue(noLocker.getMessage().contains("chỉ áp dụng tại Tủ Thư viện"), noLocker.getMessage());

        BusinessException otherLocker = assertThrows(BusinessException.class,
                () -> benefits.applyVouchers(order(15000, 5L), List.of("TUTV"), 5L));
        assertTrue(otherLocker.getMessage().contains("Tủ Thư viện"), otherLocker.getMessage());
        assertEquals(0, scoped.getUsageCount());

        LockerOrder atLocker = order(15000, 7L);
        benefits.applyVouchers(atLocker, List.of("TUTV"), 7L);
        assertEquals(0, new BigDecimal("12000").compareTo(atLocker.getTotalPrice()));
    }

    @Test
    void droneVoucherIsCheckedAgainstTheSourceLockerNotTheDestination() {
        Promotion scoped = promotion(2L, "TUTV", "3000", 7L);
        when(promotionRepository.findByCodeForUpdate("TUTV")).thenReturn(Optional.of(scoped));
        // Đơn drone: lockerId = tủ nhận (5), tủ gửi = 7.
        LockerOrder drone = order(15000, 5L);
        drone.setSourceLockerId(7L);

        benefits.applyVouchers(drone, List.of("TUTV"), drone.getSourceLockerId());

        assertEquals(0, new BigDecimal("12000").compareTo(drone.getTotalPrice()));
    }

    @Test
    void numberOfVouchersPerOrderFollowsTheAdminSetting() {
        Promotion a = promotion(1L, "A", "1000", null);
        Promotion b = promotion(2L, "B", "2000", null);
        when(promotionRepository.findByCodeForUpdate("A")).thenReturn(Optional.of(a));
        when(promotionRepository.findByCodeForUpdate("B")).thenReturn(Optional.of(b));

        // Mặc định 1 mã mỗi đơn.
        assertTrue(assertThrows(BusinessException.class,
                () -> benefits.applyVouchers(order(15000, 5L), List.of("A", "B"), 5L))
                .getMessage().contains("tối đa 1 mã"));

        // 0 = tạm ngưng mọi mã.
        assertTrue(assertThrows(BusinessException.class,
                () -> service(Map.of("app.order.max-vouchers-per-order", 0))
                        .applyVouchers(order(15000, 5L), List.of("A"), 5L))
                .getMessage().contains("tạm ngưng"));

        // > 1: mọi mã phải dùng kèm được — kiểm cả hai mã, không chỉ mã sau.
        OrderBenefitService two = service(Map.of("app.order.max-vouchers-per-order", 2));
        b.setStackable(true);
        assertTrue(assertThrows(BusinessException.class,
                () -> two.applyVouchers(order(15000, 5L), List.of("A", "B"), 5L))
                .getMessage().contains("Mã A"));

        a.setStackable(true);
        LockerOrder order = order(15000, 5L);
        two.applyVouchers(order, List.of("A", "B"), 5L);
        assertEquals(0, new BigDecimal("3000").compareTo(order.getDiscount()));
        assertEquals("A,B", order.getAppliedPromotionCodes());
        assertEquals(0, new BigDecimal("12000").compareTo(order.getTotalPrice()));
    }

    @Test
    void usageLimitsAreCheckedOnTheLockedPromotionRow() {
        Promotion soldOut = promotion(3L, "HET", "1000", null);
        soldOut.setTotalUsageLimit(10);
        soldOut.setUsageCount(10);
        when(promotionRepository.findByCodeForUpdate("HET")).thenReturn(Optional.of(soldOut));
        assertTrue(assertThrows(BusinessException.class,
                () -> benefits.applyVouchers(order(15000, 5L), List.of("HET"), 5L))
                .getMessage().contains("đã hết lượt"));

        Promotion oncePerUser = promotion(4L, "MOTLAN", "1000", null);
        oncePerUser.setPerUserLimit(1);
        when(promotionRepository.findByCodeForUpdate("MOTLAN")).thenReturn(Optional.of(oncePerUser));
        when(promotionUsageRepository.countByPromotionIdAndUserId(4L, 44L)).thenReturn(1L);
        assertTrue(assertThrows(BusinessException.class,
                () -> benefits.applyVouchers(order(15000, 5L), List.of("MOTLAN"), 5L))
                .getMessage().contains("dùng hết số lần"));

        verify(promotionRepository, never()).findByCodeIgnoreCase(any());
        verify(promotionUsageRepository, never()).save(any());
    }

    @Test
    void minimumOrderAmountIsCheckedAgainstTheOriginalPrice() {
        Promotion big = promotion(5L, "LON", "5000", null);
        big.setMinOrderAmount(new BigDecimal("20000"));

        assertEquals("đơn chưa đạt giá trị tối thiểu 20.000 đ",
                benefits.ineligibleReason(big, null, null, new BigDecimal("15000"), true));
        assertNull(benefits.ineligibleReason(big, null, null, new BigDecimal("20000"), true));
        // Admin tra mã theo tủ: không kiểm tủ.
        Promotion scoped = promotion(2L, "TUTV", "3000", 7L);
        assertNull(benefits.ineligibleReason(scoped, null, null, null, false));
    }

    @Test
    void releaseReturnsVoucherUsageAndRefundsPointsAfterCommit() {
        LockerOrder order = order(15000, 5L);
        order.setPointsUsed(50);
        order.setPointsDiscount(new BigDecimal("5000"));
        Promotion voucher = promotion(1L, "GIAM5K", "5000", null);
        voucher.setUsageCount(3);
        PromotionUsage usage = new PromotionUsage();
        usage.setPromotionId(1L);
        usage.setUserId(44L);
        usage.setOrderId(21L);
        PromotionClaim claim = new PromotionClaim();
        claim.setStatus("USED");
        claim.setUsedAt(LocalDateTime.now());
        when(promotionUsageRepository.findByOrderId(21L)).thenReturn(List.of(usage));
        when(promotionRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(voucher));
        when(promotionClaimRepository.findByPromotionIdAndUserId(1L, 44L)).thenReturn(Optional.of(claim));
        when(loyaltyClient.refund(21L)).thenReturn(ApiResponse.ok(new LoyaltyRedemption.Refund(50)));

        benefits.releaseBenefits(order);

        assertEquals(2, voucher.getUsageCount());
        assertEquals("SAVED", claim.getStatus());
        assertNull(claim.getUsedAt());
        verify(promotionUsageRepository).delete(usage);
        verify(loyaltyClient).refund(21L);
        verify(orderRepository).markPointsRefunded(eq(21L), any());
    }

    @Test
    void releasingTwiceDoesNothingTheSecondTime() {
        LockerOrder order = order(15000, 5L);
        order.setPointsUsed(50);
        order.setPointsRefundedAt(LocalDateTime.now());
        when(promotionUsageRepository.findByOrderId(21L)).thenReturn(List.of());

        benefits.releaseBenefits(order);

        verify(promotionRepository, never()).findByIdForUpdate(anyLong());
        verify(loyaltyClient, never()).refund(anyLong());
    }

    @Test
    void failedPointRefundIsRetriedLater() {
        LockerOrder order = order(15000, 5L);
        order.setPointsUsed(50);
        when(loyaltyClient.refund(21L)).thenThrow(new RuntimeException("loyalty-service down"));

        benefits.releaseBenefits(order);
        verify(orderRepository, never()).markPointsRefunded(anyLong(), any());

        when(orderRepository.findPointsRefundPending(any())).thenReturn(List.of(21L, 22L));
        org.mockito.Mockito.doReturn(ApiResponse.ok(new LoyaltyRedemption.Refund(50))).when(loyaltyClient).refund(21L);
        org.mockito.Mockito.doThrow(new RuntimeException("still down")).when(loyaltyClient).refund(22L);

        assertEquals(1, benefits.retryPendingPointRefunds(50));
        verify(orderRepository).markPointsRefunded(eq(21L), any());
        verify(orderRepository, never()).markPointsRefunded(eq(22L), any());
    }

    @Test
    void ordersCoveredByDiscountsCollectedNoMoney() {
        LockerOrder free = order(15000, 5L);
        free.setDiscount(new BigDecimal("10000"));
        free.setPointsDiscount(new BigDecimal("5000"));
        free.setTotalPrice(BigDecimal.ZERO);
        free.setPaymentStatus("PAID");
        free.setPaidAmount(BigDecimal.ZERO);
        assertFalse(OrderBenefitService.collectedMoney(free));

        LockerOrder paid = order(15000, 5L);
        paid.setPaymentStatus("PAID");
        paid.setPaidAmount(new BigDecimal("15000"));
        assertTrue(OrderBenefitService.collectedMoney(paid));
    }

    @Test
    void exchangeOnlyVoucherNeedsAHeldClaim() {
        Promotion voucher = promotion(9L, "DOI10K", "10000", null);
        voucher.setPointsCost(10_000);
        when(promotionRepository.findByCodeForUpdate("DOI10K")).thenReturn(Optional.of(voucher));
        when(promotionClaimRepository.findByPromotionIdAndUserId(9L, 44L)).thenReturn(Optional.empty());

        // Biết mã nhưng chưa đổi điểm ⇒ không áp được, kể cả khi chỉ tra mã.
        BusinessException rejected = assertThrows(BusinessException.class,
                () -> benefits.applyVouchers(order(20000, null), List.of("DOI10K"), null));
        assertTrue(rejected.getMessage().contains("đổi 10.000 điểm"));
        assertTrue(benefits.ineligibleReason(voucher, null, null, null, true).contains("đổi 10.000 điểm"));

        PromotionClaim held = new PromotionClaim();
        held.setStatus("SAVED");
        when(promotionClaimRepository.findByPromotionIdAndUserId(9L, 44L)).thenReturn(Optional.of(held));
        LockerOrder order = order(20000, null);
        benefits.applyVouchers(order, List.of("DOI10K"), null);

        assertEquals(0, new BigDecimal("10000").compareTo(order.getDiscount()));
        assertEquals("USED", held.getStatus());
    }

    private static LockerOrder order(int price, Long lockerId) {
        LockerOrder order = new LockerOrder();
        order.setId(21L);
        order.setOrderCode("ORD-21");
        order.setUserId(44L);
        order.setLockerId(lockerId);
        order.setOriginalPrice(BigDecimal.valueOf(price));
        order.setTotalPrice(BigDecimal.valueOf(price));
        order.setPaymentStatus("UNPAID");
        return order;
    }

    private static Promotion promotion(Long id, String code, String fixedAmount, Long lockerId) {
        Promotion promotion = new Promotion();
        promotion.setId(id);
        promotion.setCode(code);
        promotion.setName(code);
        promotion.setDiscountType("FIXED_AMOUNT");
        promotion.setDiscountValue(new BigDecimal(fixedAmount));
        promotion.setLockerId(lockerId);
        promotion.setStatus("ACTIVE");
        return promotion;
    }

    static LockerInfo locker(Long id, String code, String name) {
        return new LockerInfo(id, null, code, name, "ACTIVE", "Toà Alpha", null, null, false, null, 0, 0);
    }
}

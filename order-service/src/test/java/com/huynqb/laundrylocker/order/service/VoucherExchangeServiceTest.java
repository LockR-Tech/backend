package com.huynqb.laundrylocker.order.service;

import com.huynqb.laundrylocker.common.dto.ApiResponse;
import com.huynqb.laundrylocker.common.exception.BusinessException;
import com.huynqb.laundrylocker.order.client.LockerLookupClient;
import com.huynqb.laundrylocker.order.client.LoyaltyClient;
import com.huynqb.laundrylocker.order.dto.ExchangeableVoucherView;
import com.huynqb.laundrylocker.order.dto.LoyaltyRedemption;
import com.huynqb.laundrylocker.order.dto.LoyaltyVoucherExchange;
import com.huynqb.laundrylocker.order.dto.VoucherExchangeResult;
import com.huynqb.laundrylocker.order.model.Promotion;
import com.huynqb.laundrylocker.order.model.PromotionClaim;
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
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/// Đổi điểm lấy voucher: trừ điểm qua loyalty rồi voucher vào "Ưu đãi của tôi"; không đủ điểm, đang giữ
/// voucher, mã thường, loyalty lỗi.
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class VoucherExchangeServiceTest {

    @Mock PromotionRepository promotionRepository;
    @Mock PromotionUsageRepository promotionUsageRepository;
    @Mock PromotionClaimRepository promotionClaimRepository;
    @Mock LockerOrderRepository orderRepository;
    @Mock LoyaltyClient loyaltyClient;
    @Mock LockerLookupClient lockerLookupClient;

    private VoucherExchangeService service;
    private Promotion voucher;

    @BeforeEach
    void setUp() {
        PromotionViews views = new PromotionViews(lockerLookupClient);
        OrderBenefitService benefits = new OrderBenefitService(promotionRepository, promotionUsageRepository,
                promotionClaimRepository, orderRepository, loyaltyClient, views, TestOrderRules.of(Map.of()));
        service = new VoucherExchangeService(promotionRepository, promotionClaimRepository, benefits, views, loyaltyClient);
        voucher = promotion(9L, "DOI10K", 10_000);
        when(promotionRepository.findByIdForUpdate(9L)).thenReturn(Optional.of(voucher));
        when(promotionClaimRepository.findByPromotionIdAndUserId(9L, 44L)).thenReturn(Optional.empty());
        when(promotionClaimRepository.save(any(PromotionClaim.class))).thenAnswer(inv -> inv.getArgument(0));
        when(lockerLookupClient.getLockers(any())).thenReturn(ApiResponse.ok(List.of()));
    }

    @Test
    void exchangeSpendsPointsAndSavesVoucher() {
        when(loyaltyClient.exchangeVoucher(any()))
                .thenReturn(ApiResponse.ok(new LoyaltyVoucherExchange.Result(true, 10_000, 190_000)));

        VoucherExchangeResult result = service.exchange(9L, 44L);

        assertEquals(10_000, result.pointsSpent());
        assertEquals(190_000, result.balance());
        assertEquals("DOI10K", result.voucher().code());
        ArgumentCaptor<LoyaltyVoucherExchange.Request> request =
                ArgumentCaptor.forClass(LoyaltyVoucherExchange.Request.class);
        verify(loyaltyClient).exchangeVoucher(request.capture());
        assertEquals(44L, request.getValue().userId());
        assertEquals(10_000, request.getValue().points());
        assertTrue(request.getValue().reference().startsWith("VX-"));
        ArgumentCaptor<PromotionClaim> claim = ArgumentCaptor.forClass(PromotionClaim.class);
        verify(promotionClaimRepository).save(claim.capture());
        assertEquals("SAVED", claim.getValue().getStatus());
        assertEquals(44L, claim.getValue().getUserId());
    }

    @Test
    void usedVoucherCanBeExchangedAgain() {
        PromotionClaim used = new PromotionClaim();
        used.setPromotionId(9L);
        used.setUserId(44L);
        used.setStatus("USED");
        when(promotionClaimRepository.findByPromotionIdAndUserId(9L, 44L)).thenReturn(Optional.of(used));
        when(loyaltyClient.exchangeVoucher(any()))
                .thenReturn(ApiResponse.ok(new LoyaltyVoucherExchange.Result(true, 10_000, 0)));

        service.exchange(9L, 44L);

        assertEquals("SAVED", used.getStatus());
    }

    @Test
    void notEnoughPointsSavesNothing() {
        when(loyaltyClient.exchangeVoucher(any()))
                .thenReturn(ApiResponse.ok(new LoyaltyVoucherExchange.Result(false, 0, 9_000)));

        BusinessException ex = assertThrows(BusinessException.class, () -> service.exchange(9L, 44L));

        assertEquals("LOYALTY_POINTS_INSUFFICIENT", ex.getCode());
        assertTrue(ex.getMessage().contains("cần 10.000 điểm, bạn có 9.000 điểm"));
        verify(promotionClaimRepository, never()).save(any());
    }

    @Test
    void heldVoucherCannotBeExchangedAgainUntilUsed() {
        PromotionClaim held = new PromotionClaim();
        held.setStatus("SAVED");
        when(promotionClaimRepository.findByPromotionIdAndUserId(9L, 44L)).thenReturn(Optional.of(held));

        BusinessException ex = assertThrows(BusinessException.class, () -> service.exchange(9L, 44L));

        assertEquals("VOUCHER_ALREADY_HELD", ex.getCode());
        verify(loyaltyClient, never()).exchangeVoucher(any());
    }

    @Test
    void ordinaryPromotionIsNotExchangeable() {
        voucher.setPointsCost(null);

        assertThrows(BusinessException.class, () -> service.exchange(9L, 44L));
        verify(loyaltyClient, never()).exchangeVoucher(any());
    }

    @Test
    void loyaltyFailureRefundsAndSavesNothing() {
        when(loyaltyClient.exchangeVoucher(any())).thenThrow(new RuntimeException("timeout"));
        when(loyaltyClient.refundVoucherExchange(anyString()))
                .thenReturn(ApiResponse.ok(new LoyaltyRedemption.Refund(10_000)));

        BusinessException ex = assertThrows(BusinessException.class, () -> service.exchange(9L, 44L));

        assertEquals("LOYALTY_UNAVAILABLE", ex.getCode());
        verify(loyaltyClient).refundVoucherExchange(anyString());
        verify(promotionClaimRepository, never()).save(any());
    }

    @Test
    void listShowsOnlyExchangeableVouchersCheapestFirstWithHeldFlag() {
        Promotion big = promotion(10L, "DOI50K", 50_000);
        Promotion ordinary = promotion(11L, "GIAM5K", 5_000);
        ordinary.setPointsCost(null);
        when(promotionRepository.findByStatus("ACTIVE")).thenReturn(List.of(big, ordinary, voucher));
        PromotionClaim held = new PromotionClaim();
        held.setPromotionId(10L);
        held.setStatus("SAVED");
        when(promotionClaimRepository.findByUserIdOrderByCreatedAtDesc(44L)).thenReturn(List.of(held));

        List<ExchangeableVoucherView> list = service.exchangeable(44L);

        assertEquals(List.of("DOI10K", "DOI50K"), list.stream().map(v -> v.promotion().code()).toList());
        assertFalse(list.get(0).held());
        assertTrue(list.get(1).held());
        assertEquals(10_000, list.get(0).promotion().pointsCost());
    }

    private static Promotion promotion(Long id, String code, int pointsCost) {
        Promotion promotion = new Promotion();
        promotion.setId(id);
        promotion.setCode(code);
        promotion.setName(code);
        promotion.setDiscountType("FIXED_AMOUNT");
        promotion.setDiscountValue(BigDecimal.valueOf(pointsCost));
        promotion.setPointsCost(pointsCost);
        promotion.setStatus("ACTIVE");
        return promotion;
    }
}

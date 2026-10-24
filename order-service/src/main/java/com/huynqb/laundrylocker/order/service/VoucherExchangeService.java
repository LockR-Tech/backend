package com.huynqb.laundrylocker.order.service;

import com.huynqb.laundrylocker.common.dto.ApiResponse;
import com.huynqb.laundrylocker.common.exception.BusinessException;
import com.huynqb.laundrylocker.common.exception.NotFoundException;
import com.huynqb.laundrylocker.order.client.LoyaltyClient;
import com.huynqb.laundrylocker.order.dto.ExchangeableVoucherView;
import com.huynqb.laundrylocker.order.dto.LoyaltyVoucherExchange;
import com.huynqb.laundrylocker.order.dto.VoucherExchangeResult;
import com.huynqb.laundrylocker.order.model.Promotion;
import com.huynqb.laundrylocker.order.model.PromotionClaim;
import com.huynqb.laundrylocker.order.repository.PromotionClaimRepository;
import com.huynqb.laundrylocker.order.repository.PromotionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/// Đổi điểm lấy voucher. Voucher đổi được là mã có `pointsCost > 0`; đổi xong khách có một voucher SAVED
/// trong "Ưu đãi của tôi" (promotion_claims) và áp vào đơn như mã thường — {@link OrderBenefitService}
/// chỉ cho người đang giữ voucher áp mã này. Mỗi lúc giữ tối đa một voucher mỗi loại: dùng xong (USED)
/// mới đổi tiếp. Điểm do loyalty-service trừ, idempotent theo mã một lần đổi (`reference`).
@Slf4j
@Service
@RequiredArgsConstructor
public class VoucherExchangeService {

    private final PromotionRepository promotionRepository;
    private final PromotionClaimRepository promotionClaimRepository;
    private final OrderBenefitService benefits;
    private final PromotionViews promotionViews;
    private final LoyaltyClient loyaltyClient;

    /// Voucher đang đổi được, rẻ → đắt; `held` = `userId` đang giữ voucher đó chưa dùng.
    @Transactional(readOnly = true)
    public List<ExchangeableVoucherView> exchangeable(Long userId) {
        List<Promotion> promotions = promotionRepository.findByStatus("ACTIVE").stream()
                .filter(Promotion::activeNow)
                .filter(Promotion::exchangeable)
                .sorted(Comparator.comparing(Promotion::getPointsCost))
                .toList();
        Set<Long> held = userId == null ? Set.of()
                : promotionClaimRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                        .filter(claim -> "SAVED".equals(claim.getStatus()))
                        .map(PromotionClaim::getPromotionId)
                        .collect(Collectors.toSet());
        return promotionViews.views(promotions, false).stream()
                .map(view -> new ExchangeableVoucherView(view, held.contains(view.id())))
                .toList();
    }

    /// Trừ `pointsCost` điểm của khách rồi cho voucher vào "Ưu đãi của tôi". Hàng mã bị khoá nên hai lần đổi
    /// cùng lúc của một khách không cùng qua được bước kiểm "đang giữ". Lưu voucher lỗi ⇒ hoàn điểm.
    @Transactional
    public VoucherExchangeResult exchange(Long promotionId, Long userId) {
        Promotion promotion = promotionRepository.findByIdForUpdate(promotionId)
                .orElseThrow(() -> new NotFoundException("Promotion", promotionId));
        if (!promotion.exchangeable()) {
            throw new BusinessException("PROMOTION_NOT_EXCHANGEABLE", "Mã này không đổi bằng điểm");
        }
        String reason = benefits.ineligibleReason(promotion, null, userId, null, false);
        if (reason != null) {
            throw new BusinessException("PROMOTION_INVALID", "Không đổi được voucher: " + reason);
        }
        PromotionClaim claim = promotionClaimRepository.findByPromotionIdAndUserId(promotionId, userId).orElse(null);
        if (claim != null && "SAVED".equals(claim.getStatus())) {
            throw new BusinessException("VOUCHER_ALREADY_HELD",
                    "Bạn đang có voucher này trong Ưu đãi của tôi — dùng xong mới đổi tiếp được",
                    HttpStatus.CONFLICT);
        }

        String reference = "VX-" + UUID.randomUUID().toString().replace("-", "");
        LoyaltyVoucherExchange.Result result;
        try {
            ApiResponse<LoyaltyVoucherExchange.Result> response = loyaltyClient.exchangeVoucher(
                    new LoyaltyVoucherExchange.Request(userId, promotion.getPointsCost(), reference));
            result = response == null ? null : response.data();
        } catch (RuntimeException ex) {
            log.warn("Voucher exchange {} for user {} failed: {}", promotionId, userId, ex.getMessage());
            // Lời gọi có thể đã trừ điểm dù phản hồi lỗi/quá hạn: hoàn bù (idempotent, 0 nếu chưa trừ).
            refundQuietly(reference);
            throw new BusinessException("LOYALTY_UNAVAILABLE",
                    "Chưa đổi được voucher lúc này, vui lòng thử lại", HttpStatus.SERVICE_UNAVAILABLE);
        }
        if (result == null || !result.exchanged()) {
            int balance = result == null ? 0 : result.balance();
            throw new BusinessException("LOYALTY_POINTS_INSUFFICIENT",
                    "Không đủ điểm: voucher cần " + OrderBenefitService.points(promotion.getPointsCost())
                            + ", bạn có " + OrderBenefitService.points(balance));
        }
        afterRollback(() -> refundQuietly(reference));

        if (claim == null) {
            claim = new PromotionClaim();
            claim.setPromotionId(promotionId);
            claim.setUserId(userId);
        }
        claim.setStatus("SAVED");
        claim.setUsedAt(null);
        promotionClaimRepository.save(claim);
        return new VoucherExchangeResult(promotionViews.view(promotion, false), result.pointsSpent(), result.balance());
    }

    private void refundQuietly(String reference) {
        try {
            loyaltyClient.refundVoucherExchange(reference);
        } catch (RuntimeException ex) {
            log.error("Could not refund loyalty points of voucher exchange {}: {}", reference, ex.getMessage());
        }
    }

    private static void afterRollback(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_ROLLED_BACK) {
                    action.run();
                }
            }
        });
    }
}

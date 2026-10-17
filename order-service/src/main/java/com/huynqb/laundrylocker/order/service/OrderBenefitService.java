package com.huynqb.laundrylocker.order.service;

import com.huynqb.laundrylocker.common.dto.ApiResponse;
import com.huynqb.laundrylocker.common.exception.BusinessException;
import com.huynqb.laundrylocker.order.client.LoyaltyClient;
import com.huynqb.laundrylocker.order.dto.LoyaltyRedemption;
import com.huynqb.laundrylocker.order.model.LockerOrder;
import com.huynqb.laundrylocker.order.model.Promotion;
import com.huynqb.laundrylocker.order.model.PromotionUsage;
import com.huynqb.laundrylocker.order.repository.LockerOrderRepository;
import com.huynqb.laundrylocker.order.repository.PromotionClaimRepository;
import com.huynqb.laundrylocker.order.repository.PromotionRepository;
import com.huynqb.laundrylocker.order.repository.PromotionUsageRepository;
import com.huynqb.laundrylocker.order.settings.OrderRules;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Ưu đãi của một đơn: mã giảm giá (voucher, có thể theo tủ) và điểm thưởng dùng thanh toán.
 *
 * <p>Thứ tự tính giá: {@code originalPrice} → trừ mã giảm giá ({@code discount}) → trừ điểm trên phần còn
 * lại ({@code pointsDiscount}) → {@code totalPrice = max(original − discount − pointsDiscount, 0)}.
 * Đơn còn 0đ được ghi đã thanh toán ngay ({@link PaidOrderEffects#settleIfFree}).
 *
 * <p>Mọi đường làm đơn thành {@code CANCELED} và lúc khoản thanh toán thành {@code REFUNDED} đều gọi
 * {@link #releaseBenefits}: trả lượt mã và hoàn điểm, gọi lại nhiều lần vẫn an toàn.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderBenefitService {

    private final PromotionRepository promotionRepository;
    private final PromotionUsageRepository promotionUsageRepository;
    private final PromotionClaimRepository promotionClaimRepository;
    private final LockerOrderRepository orderRepository;
    private final LoyaltyClient loyaltyClient;
    private final PromotionViews promotionViews;
    private final OrderRules rules;

    // ------------------------------------------------------------------ mã giảm giá

    /// Mã khách nhập: bỏ trống, chuẩn hoá chữ hoa, bỏ trùng (cùng một mã nhập hai lần không giảm hai lần).
    public static List<String> normalizeCodes(String promotionCode, List<String> promotionCodes) {
        Set<String> codes = new LinkedHashSet<>();
        if (StringUtils.hasText(promotionCode)) {
            codes.add(promotionCode.trim().toUpperCase(Locale.ROOT));
        }
        if (promotionCodes != null) {
            promotionCodes.stream()
                    .filter(StringUtils::hasText)
                    .map(code -> code.trim().toUpperCase(Locale.ROOT))
                    .forEach(codes::add);
        }
        return List.copyOf(codes);
    }

    /// Áp mã vào đơn vừa lưu (đã có id). Mã không dùng được thì THROW 400 PROMOTION_INVALID — khách không
    /// bị mất giảm giá mà không hay. Kiểm: số mã ≤ `app.order.max-vouchers-per-order`, hiệu lực, tủ áp dụng
    /// (`eligibleLockerId` — tủ của đơn; đơn drone là TỦ GỬI; mã theo tủ mà đơn không có tủ ⇒ từ chối), đơn
    /// tối thiểu, trần tổng lượt và trần lượt mỗi khách. Hàng mã bị khoá trong lúc kiểm và cộng lượt.
    public void applyVouchers(LockerOrder order, List<String> codes, Long eligibleLockerId) {
        if (codes == null || codes.isEmpty()) {
            return;
        }
        int max = rules.maxVouchersPerOrder();
        if (max <= 0) {
            throw new BusinessException("PROMOTION_INVALID", "Hệ thống đang tạm ngưng áp dụng mã giảm giá");
        }
        if (codes.size() > max) {
            throw new BusinessException("PROMOTION_INVALID", "Mỗi đơn chỉ dùng tối đa " + max + " mã giảm giá");
        }
        // Khoá theo thứ tự mã cố định để hai đơn cùng dùng hai mã không khoá chéo nhau.
        Map<String, Promotion> locked = new HashMap<>();
        codes.stream().sorted().forEach(code ->
                locked.put(code, promotionRepository.findByCodeForUpdate(code).orElse(null)));

        BigDecimal base = nz(order.getOriginalPrice());
        BigDecimal remaining = base;
        BigDecimal discount = BigDecimal.ZERO;
        List<String> applied = new ArrayList<>();
        for (String code : codes) {
            Promotion promotion = locked.get(code);
            String reason = ineligibleReason(promotion, eligibleLockerId, order.getUserId(), base, true);
            if (reason != null) {
                throw new BusinessException("PROMOTION_INVALID", "Mã " + code + ": " + reason);
            }
            // Nhiều mã một đơn (admin cho phép > 1): mọi mã đều phải đánh dấu dùng kèm được.
            if (codes.size() > 1 && !Boolean.TRUE.equals(promotion.getStackable())) {
                throw new BusinessException("PROMOTION_INVALID", "Mã " + code + ": không thể dùng kèm mã khác");
            }
            BigDecimal amount = discountAmount(promotion, base).min(remaining).max(BigDecimal.ZERO);
            remaining = remaining.subtract(amount);
            discount = discount.add(amount);
            applied.add(promotion.getCode());
            promotion.setUsageCount((promotion.getUsageCount() == null ? 0 : promotion.getUsageCount()) + 1);
            promotionRepository.save(promotion);

            PromotionUsage usage = new PromotionUsage();
            usage.setPromotionId(promotion.getId());
            usage.setUserId(order.getUserId());
            usage.setOrderId(order.getId());
            usage.setDiscountApplied(amount);
            promotionUsageRepository.save(usage);

            promotionClaimRepository
                    .findByPromotionIdAndUserId(promotion.getId(), order.getUserId())
                    .filter(claim -> "SAVED".equals(claim.getStatus()))
                    .ifPresent(claim -> {
                        claim.setStatus("USED");
                        claim.setUsedAt(LocalDateTime.now());
                        promotionClaimRepository.save(claim);
                    });
        }
        order.setPromotionCode(applied.getFirst());
        order.setAppliedPromotionCodes(String.join(",", applied));
        order.setDiscount(discount);
        recomputeTotal(order);
    }

    /// Lý do mã không dùng được (null = dùng được). `checkLocker` = false chỉ cho admin tra mã; khách và tạo
    /// đơn luôn kiểm tủ: mã theo tủ mà `lockerId` thiếu hoặc khác ⇒ không dùng được, lý do nêu tên tủ.
    /// `userId` null ⇒ bỏ qua trần lượt mỗi khách; `orderTotal` null ⇒ bỏ qua đơn tối thiểu.
    public String ineligibleReason(
            Promotion promotion, Long lockerId, Long userId, BigDecimal orderTotal, boolean checkLocker) {
        if (promotion == null || !promotion.activeNow()) {
            return "không hợp lệ hoặc đã hết hạn";
        }
        if (checkLocker && promotion.getLockerId() != null && !promotion.getLockerId().equals(lockerId)) {
            String where = promotionViews.lockerLabel(promotion.getLockerId());
            return lockerId == null
                    ? "chỉ áp dụng tại " + where + " — hãy chọn tủ này để dùng mã"
                    : "chỉ áp dụng tại " + where;
        }
        if (orderTotal != null && promotion.getMinOrderAmount() != null
                && orderTotal.compareTo(promotion.getMinOrderAmount()) < 0) {
            return "đơn chưa đạt giá trị tối thiểu " + vnd(promotion.getMinOrderAmount());
        }
        if (promotion.getTotalUsageLimit() != null
                && (promotion.getUsageCount() == null ? 0 : promotion.getUsageCount()) >= promotion.getTotalUsageLimit()) {
            return "đã hết lượt sử dụng";
        }
        if (promotion.getPerUserLimit() != null && userId != null
                && promotionUsageRepository.countByPromotionIdAndUserId(promotion.getId(), userId)
                >= promotion.getPerUserLimit()) {
            return "bạn đã dùng hết số lần cho phép";
        }
        return null;
    }

    static BigDecimal discountAmount(Promotion promotion, BigDecimal orderTotal) {
        BigDecimal value = nz(promotion.getDiscountValue());
        BigDecimal discount =
                "PERCENTAGE".equalsIgnoreCase(promotion.getDiscountType())
                        ? orderTotal.multiply(value).divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP)
                        : value;
        if (promotion.getMaxDiscountAmount() != null && discount.compareTo(promotion.getMaxDiscountAmount()) > 0) {
            discount = promotion.getMaxDiscountAmount();
        }
        return discount.min(orderTotal);
    }

    /// Trả lượt mã của đơn: xoá promotion_usages, giảm usage_count (dưới khoá hàng mã), voucher đã lưu về
    /// SAVED để khách dùng lại. Gọi lại khi đã trả thì không còn usage nào ⇒ không làm gì.
    void refundPromotionUsages(LockerOrder order) {
        for (PromotionUsage usage : promotionUsageRepository.findByOrderId(order.getId())) {
            promotionRepository.findByIdForUpdate(usage.getPromotionId()).ifPresent(promotion -> {
                promotion.setUsageCount(Math.max(0, (promotion.getUsageCount() == null ? 0 : promotion.getUsageCount()) - 1));
                promotionRepository.save(promotion);
            });
            promotionClaimRepository
                    .findByPromotionIdAndUserId(usage.getPromotionId(), usage.getUserId())
                    .filter(claim -> "USED".equals(claim.getStatus()))
                    .ifPresent(claim -> {
                        claim.setStatus("SAVED");
                        claim.setUsedAt(null);
                        promotionClaimRepository.save(claim);
                    });
            promotionUsageRepository.delete(usage);
        }
    }

    // ------------------------------------------------------------------ điểm thưởng

    /// Trừ điểm cho đơn vừa lưu (đã có id), tính trên phần còn lại sau mã giảm giá; loyalty-service kẹp số
    /// điểm theo quy tắc admin. loyalty lỗi/không phản hồi ⇒ đơn vẫn tạo, không dùng điểm (không chặn đặt
    /// đơn). Giao dịch tạo đơn rollback sau khi đã trừ ⇒ hoàn bù sau khi giao dịch kết thúc.
    public void redeemPoints(LockerOrder order, Integer pointsToUse) {
        if (pointsToUse == null || pointsToUse <= 0 || order.getId() == null || order.getUserId() == null) {
            return;
        }
        BigDecimal remaining = nz(order.getOriginalPrice()).subtract(nz(order.getDiscount())).max(BigDecimal.ZERO);
        if (remaining.signum() <= 0) {
            return;
        }
        Long orderId = order.getId();
        LoyaltyRedemption.Result result;
        try {
            ApiResponse<LoyaltyRedemption.Result> response = loyaltyClient.redeem(
                    new LoyaltyRedemption.Request(order.getUserId(), orderId, remaining, pointsToUse));
            result = response == null ? null : response.data();
        } catch (RuntimeException ex) {
            log.warn("Loyalty redemption for order {} failed, creating it without points: {}", orderId, ex.getMessage());
            // Lời gọi có thể đã trừ điểm dù phản hồi lỗi/quá hạn: hoàn bù (idempotent, 0 nếu chưa trừ).
            afterCompletion(false, () -> refundPointsQuietly(orderId));
            return;
        }
        if (result == null || result.pointsUsed() <= 0) {
            return;
        }
        order.setPointsUsed(result.pointsUsed());
        order.setPointsDiscount(nz(result.discountAmount()).min(remaining).max(BigDecimal.ZERO));
        recomputeTotal(order);
        afterCompletion(true, () -> refundPointsQuietly(orderId));
    }

    // ------------------------------------------------------------------ trả ưu đãi

    /// Trả lượt mã + hoàn điểm của đơn vừa huỷ / vừa hoàn tiền. Idempotent. Điểm hoàn SAU khi giao dịch
    /// commit (huỷ bị rollback thì không hoàn); loyalty lỗi thì {@link #retryPendingPointRefunds} thử lại.
    public void releaseBenefits(LockerOrder order) {
        if (order == null || order.getId() == null) {
            return;
        }
        refundPromotionUsages(order);
        if (order.getPointsUsed() != null && order.getPointsUsed() > 0 && order.getPointsRefundedAt() == null) {
            Long orderId = order.getId();
            afterCommit(() -> refundPointsAndMark(orderId));
        }
    }

    /// Job: hoàn điểm cho đơn huỷ/hoàn tiền mà lần hoàn trước lỗi. Trả số đơn đã hoàn xong.
    public int retryPendingPointRefunds(int limit) {
        int done = 0;
        for (Long orderId : orderRepository.findPointsRefundPending(PageRequest.of(0, Math.max(1, limit)))) {
            if (refundPointsAndMark(orderId)) {
                done++;
            }
        }
        return done;
    }

    private boolean refundPointsAndMark(Long orderId) {
        try {
            ApiResponse<LoyaltyRedemption.Refund> response = loyaltyClient.refund(orderId);
            LoyaltyRedemption.Refund refund = response == null ? null : response.data();
            orderRepository.markPointsRefunded(orderId, LocalDateTime.now());
            log.info("Loyalty points of order {} refunded ({} points)", orderId,
                    refund == null ? "?" : refund.pointsRefunded());
            return true;
        } catch (RuntimeException ex) {
            log.warn("Could not refund loyalty points of order {}, will retry: {}", orderId, ex.getMessage());
            return false;
        }
    }

    private void refundPointsQuietly(Long orderId) {
        try {
            loyaltyClient.refund(orderId);
        } catch (RuntimeException ex) {
            log.error("Could not compensate loyalty redemption of order {} — check manually: {}",
                    orderId, ex.getMessage());
        }
    }

    // ------------------------------------------------------------------ tiện ích

    /// total = max(original − discount − pointsDiscount, 0).
    static void recomputeTotal(LockerOrder order) {
        order.setTotalPrice(nz(order.getOriginalPrice())
                .subtract(nz(order.getDiscount()))
                .subtract(nz(order.getPointsDiscount()))
                .max(BigDecimal.ZERO));
    }

    /// Đơn đã thu được tiền thật (để quyết định có tạo yêu cầu hoàn tiền không). Đơn được mã giảm giá /
    /// điểm thưởng trả hết (PAID, paidAmount 0, totalPrice 0) không thu đồng nào.
    public static boolean collectedMoney(LockerOrder order) {
        if (order.getPaidAmount() != null && order.getPaidAmount().signum() > 0) {
            return true;
        }
        return "PAID".equalsIgnoreCase(order.getPaymentStatus()) && !coveredByDiscounts(order);
    }

    /// Tổng đơn về 0 nhờ mã giảm giá và/hoặc điểm thưởng.
    static boolean coveredByDiscounts(LockerOrder order) {
        return (order.getTotalPrice() == null || order.getTotalPrice().signum() <= 0)
                && (nz(order.getDiscount()).signum() > 0 || nz(order.getPointsDiscount()).signum() > 0);
    }

    private static BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static String vnd(BigDecimal amount) {
        return String.format(Locale.ROOT, "%,d", amount.setScale(0, RoundingMode.HALF_UP).longValue())
                .replace(',', '.') + " đ";
    }

    /// `onlyOnRollback` = true: chạy khi giao dịch rollback; false: chạy khi giao dịch kết thúc (commit
    /// hoặc rollback). Không có giao dịch: chỉ chạy ngay với trường hợp false.
    private static void afterCompletion(boolean onlyOnRollback, Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            if (!onlyOnRollback) {
                action.run();
            }
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (!onlyOnRollback || status == STATUS_ROLLED_BACK) {
                    action.run();
                }
            }
        });
    }

    private static void afterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }
}

package com.huynqb.laundrylocker.loyalty.settings;

import com.huynqb.laundrylocker.common.settings.BusinessSettings;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import static com.huynqb.laundrylocker.loyalty.settings.LoyaltySettingsCatalog.*;

/// Đọc có kiểu các quy tắc nghiệp vụ của loyalty-service. Mỗi lần gọi lấy giá trị đang áp dụng
/// (admin sửa có hiệu lực tối đa sau 30 giây, ngay lập tức trên instance nhận lệnh sửa).
@Component
@RequiredArgsConstructor
public class LoyaltyRules {

    private final BusinessSettings settings;

    /// Hạng theo tổng điểm đã tích (lifetime); mốc luôn tăng dần SILVER ≤ GOLD ≤ PLATINUM (mốc sau nhỏ hơn
    /// thì nâng lên bằng mốc trước).
    public String tierFor(int lifetimePoints) {
        TierThresholds t = tierThresholds();
        if (lifetimePoints >= t.platinum()) return "PLATINUM";
        if (lifetimePoints >= t.gold()) return "GOLD";
        if (lifetimePoints >= t.silver()) return "SILVER";
        return "BRONZE";
    }

    /// Mốc hạng đang áp dụng, đã chuẩn hoá tăng dần — dùng chung cho tierFor và thống kê theo hạng.
    public TierThresholds tierThresholds() {
        int silver = settings.getInt(TIER_SILVER_POINTS);
        int gold = Math.max(silver, settings.getInt(TIER_GOLD_POINTS));
        int platinum = Math.max(gold, settings.getInt(TIER_PLATINUM_POINTS));
        return new TierThresholds(silver, gold, platinum);
    }

    public record TierThresholds(int silver, int gold, int platinum) {
    }

    public boolean redeemEnabled() {
        return settings.getBoolean(REDEEM_ENABLED);
    }

    /// Giá trị (đ) của 1 điểm khi dùng thanh toán, tối thiểu 1.
    public int redeemVndPerPoint() {
        return Math.max(1, settings.getInt(REDEEM_VND_PER_POINT));
    }

    /// % tối đa của số tiền đơn (sau voucher) được trả bằng điểm, trong khoảng 1–100.
    public int redeemMaxPercent() {
        return Math.min(100, Math.max(1, settings.getInt(REDEEM_MAX_PERCENT)));
    }

    public int redeemMinPoints() {
        return Math.max(0, settings.getInt(REDEEM_MIN_POINTS));
    }

    /// Số điểm tối đa dùng được cho đơn `orderTotal` đồng với số dư `balance`:
    /// min(balance, floor(orderTotal × maxPercent / 100 / vndPerPoint)); dưới mức tối thiểu mỗi lần dùng
    /// hoặc tính năng đang tắt ⇒ 0.
    public int maxRedeemablePoints(int balance, java.math.BigDecimal orderTotal) {
        if (!redeemEnabled() || balance <= 0 || orderTotal == null || orderTotal.signum() <= 0) {
            return 0;
        }
        long byOrder = orderTotal
                .multiply(java.math.BigDecimal.valueOf(redeemMaxPercent()))
                .divide(java.math.BigDecimal.valueOf(100L * redeemVndPerPoint()), 0, java.math.RoundingMode.FLOOR)
                .min(java.math.BigDecimal.valueOf(Integer.MAX_VALUE))
                .longValue();
        int max = (int) Math.min(balance, byOrder);
        return max < redeemMinPoints() ? 0 : max;
    }

    /// Số điểm thực trừ khi khách xin dùng `requestedPoints`: kẹp theo {@link #maxRedeemablePoints};
    /// xin ít hơn mức tối thiểu mỗi lần dùng ⇒ 0.
    public int clampRedeemPoints(int requestedPoints, int balance, java.math.BigDecimal orderTotal) {
        if (requestedPoints <= 0) {
            return 0;
        }
        int points = Math.min(requestedPoints, maxRedeemablePoints(balance, orderTotal));
        return points < Math.max(1, redeemMinPoints()) ? 0 : points;
    }

    /// Số tiền giảm cho `points` điểm.
    public java.math.BigDecimal redeemValue(int points) {
        return java.math.BigDecimal.valueOf((long) Math.max(0, points) * redeemVndPerPoint());
    }

    public int stampsPerReward() {
        return settings.getInt(STAMPS_PER_REWARD);
    }

    public int pointsRewardCost() {
        return settings.getInt(POINTS_REWARD_COST);
    }

    public int stampIncrement() {
        return settings.getInt(STAMP_INCREMENT);
    }

    /// Điểm cho số tiền đơn đã thanh toán: 1 điểm mỗi `app.loyalty.vnd-per-point` đồng, làm tròn xuống.
    public int pointsFor(java.math.BigDecimal paidAmount) {
        if (paidAmount == null || paidAmount.signum() <= 0) {
            return 0;
        }
        java.math.BigDecimal points = paidAmount.divide(
                java.math.BigDecimal.valueOf(Math.max(1, settings.getInt(VND_PER_POINT))), 0, java.math.RoundingMode.FLOOR);
        return points.min(java.math.BigDecimal.valueOf(Integer.MAX_VALUE)).intValue();
    }
}

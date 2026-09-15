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

    /// Hạng theo tổng điểm; mốc luôn tăng dần SILVER ≤ GOLD ≤ PLATINUM (mốc sau nhỏ hơn thì nâng lên bằng mốc trước).
    public String tierFor(int points) {
        int silver = settings.getInt(TIER_SILVER_POINTS);
        int gold = Math.max(silver, settings.getInt(TIER_GOLD_POINTS));
        int platinum = Math.max(gold, settings.getInt(TIER_PLATINUM_POINTS));
        if (points >= platinum) return "PLATINUM";
        if (points >= gold) return "GOLD";
        if (points >= silver) return "SILVER";
        return "BRONZE";
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
}

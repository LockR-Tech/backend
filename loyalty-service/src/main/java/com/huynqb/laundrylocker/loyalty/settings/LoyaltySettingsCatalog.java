package com.huynqb.laundrylocker.loyalty.settings;

import com.huynqb.laundrylocker.common.settings.SettingDefinition;
import com.huynqb.laundrylocker.common.settings.SettingsCatalog;
import org.springframework.stereotype.Component;

import java.util.List;

import static com.huynqb.laundrylocker.common.settings.SettingDefinition.integer;

/// Quy tắc nghiệp vụ của loyalty-service admin chỉnh được (ADR-0005).
@Component
public class LoyaltySettingsCatalog implements SettingsCatalog {

    public static final String TIER_SILVER_POINTS = "app.loyalty.tier-silver-points";
    public static final String TIER_GOLD_POINTS = "app.loyalty.tier-gold-points";
    public static final String TIER_PLATINUM_POINTS = "app.loyalty.tier-platinum-points";
    public static final String STAMPS_PER_REWARD = "app.loyalty.stamps-per-reward";
    public static final String POINTS_REWARD_COST = "app.loyalty.points-reward-cost";
    public static final String STAMP_INCREMENT = "app.loyalty.stamp-increment";

    private static final String LOYALTY = "Hạng thành viên & phần thưởng";

    @Override
    public List<SettingDefinition> definitions() {
        return List.of(
                integer(TIER_SILVER_POINTS, LOYALTY, "Điểm lên hạng BẠC (SILVER)",
                        "Tổng điểm tối thiểu để đạt hạng SILVER. Hạng được tính lại khi điểm thay đổi. "
                                + "Các mốc luôn tăng dần: mốc sau nhỏ hơn mốc trước thì dùng mốc trước.",
                        500, 1, 10_000_000, "điểm").asPublic(),
                integer(TIER_GOLD_POINTS, LOYALTY, "Điểm lên hạng VÀNG (GOLD)",
                        "Tổng điểm tối thiểu để đạt hạng GOLD.", 2000, 1, 10_000_000, "điểm").asPublic(),
                integer(TIER_PLATINUM_POINTS, LOYALTY, "Điểm lên hạng BẠCH KIM (PLATINUM)",
                        "Tổng điểm tối thiểu để đạt hạng PLATINUM.", 5000, 1, 10_000_000, "điểm").asPublic(),
                integer(STAMPS_PER_REWARD, LOYALTY, "Số tem đổi một phần thưởng",
                        "Thẻ tích tem đủ số tem này thì đổi được phần thưởng tem.", 10, 1, 100, "tem").asPublic(),
                integer(POINTS_REWARD_COST, LOYALTY, "Số điểm đổi một phần thưởng",
                        "Số điểm bị trừ khi đổi phần thưởng điểm.", 1000, 1, 10_000_000, "điểm").asPublic(),
                integer(STAMP_INCREMENT, LOYALTY, "Số tem cộng mỗi lần",
                        "Số tem cộng cho khách khi lệnh cộng tem không ghi rõ số lượng.", 1, 1, 10, "tem"));
    }
}

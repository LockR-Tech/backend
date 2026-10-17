package com.huynqb.laundrylocker.loyalty.settings;

import com.huynqb.laundrylocker.common.settings.SettingDefinition;
import com.huynqb.laundrylocker.common.settings.SettingsCatalog;
import org.springframework.stereotype.Component;

import java.util.List;

import static com.huynqb.laundrylocker.common.settings.SettingDefinition.bool;
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
    public static final String VND_PER_POINT = "app.loyalty.vnd-per-point";
    public static final String REDEEM_ENABLED = "app.loyalty.redeem-enabled";
    public static final String REDEEM_VND_PER_POINT = "app.loyalty.redeem-vnd-per-point";
    public static final String REDEEM_MAX_PERCENT = "app.loyalty.redeem-max-percent";
    public static final String REDEEM_MIN_POINTS = "app.loyalty.redeem-min-points";

    private static final String LOYALTY = "Hạng thành viên & phần thưởng";
    private static final String REDEEM = "Dùng điểm thanh toán";

    @Override
    public List<SettingDefinition> definitions() {
        return List.of(
                integer(TIER_SILVER_POINTS, LOYALTY, "Điểm lên hạng BẠC (SILVER)",
                        "Tổng điểm ĐÃ TÍCH từ trước tới nay tối thiểu để đạt hạng SILVER — dùng điểm thanh toán "
                                + "không làm tụt hạng. Các mốc luôn tăng dần: mốc sau nhỏ hơn mốc trước thì dùng mốc trước.",
                        500, 1, 10_000_000, "điểm").asPublic(),
                integer(TIER_GOLD_POINTS, LOYALTY, "Điểm lên hạng VÀNG (GOLD)",
                        "Tổng điểm đã tích tối thiểu để đạt hạng GOLD.", 2000, 1, 10_000_000, "điểm").asPublic(),
                integer(TIER_PLATINUM_POINTS, LOYALTY, "Điểm lên hạng BẠCH KIM (PLATINUM)",
                        "Tổng điểm đã tích tối thiểu để đạt hạng PLATINUM.", 5000, 1, 10_000_000, "điểm").asPublic(),
                integer(STAMPS_PER_REWARD, LOYALTY, "Số tem đổi một phần thưởng",
                        "Thẻ tích tem đủ số tem này thì đổi được phần thưởng tem.", 10, 1, 100, "tem").asPublic(),
                integer(POINTS_REWARD_COST, LOYALTY, "Số điểm đổi một phần thưởng",
                        "Số điểm bị trừ khi đổi phần thưởng điểm.", 1000, 1, 10_000_000, "điểm").asPublic(),
                integer(STAMP_INCREMENT, LOYALTY, "Số tem cộng mỗi lần",
                        "Số tem cộng cho khách khi lệnh cộng tem không ghi rõ số lượng.", 1, 1, 10, "tem"),
                integer(VND_PER_POINT, LOYALTY, "Số tiền cho 1 điểm thưởng",
                        "Đơn hoàn tất được cộng 1 điểm cho mỗi khoản tiền này khách đã thanh toán, làm tròn xuống. "
                                + "Đơn 0đ hoặc bị huỷ không được cộng điểm.",
                        1000, 1, 10_000_000, "đ").asPublic(),

                bool(REDEEM_ENABLED, REDEEM, "Cho phép dùng điểm thanh toán",
                        "Bật thì khách được trừ điểm vào tiền đơn khi đặt gửi hàng, thuê ô, giao drone. "
                                + "Tắt thì đơn mới không dùng được điểm; điểm đã dùng ở đơn cũ vẫn được hoàn khi huỷ.",
                        true).asPublic(),
                integer(REDEEM_VND_PER_POINT, REDEEM, "Giá trị 1 điểm khi dùng",
                        "Mỗi điểm dùng giảm bấy nhiêu tiền cho đơn. Áp dụng cho đơn tạo sau khi lưu.",
                        100, 1, 1_000_000, "đ").asPublic(),
                integer(REDEEM_MAX_PERCENT, REDEEM, "Tỉ lệ tối đa của đơn trả bằng điểm",
                        "Phần trăm tối đa số tiền đơn (sau khi trừ mã giảm giá) được trả bằng điểm.",
                        50, 1, 100, "%").asPublic(),
                integer(REDEEM_MIN_POINTS, REDEEM, "Số điểm tối thiểu mỗi lần dùng",
                        "Khách phải dùng ít nhất bấy nhiêu điểm mỗi đơn; số điểm dùng được ít hơn thì không trừ. "
                                + "0 = không giới hạn.",
                        10, 0, 10_000_000, "điểm").asPublic());
    }
}

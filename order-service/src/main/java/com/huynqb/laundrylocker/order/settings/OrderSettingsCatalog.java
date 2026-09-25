package com.huynqb.laundrylocker.order.settings;

import com.huynqb.laundrylocker.common.settings.SettingDefinition;
import com.huynqb.laundrylocker.common.settings.SettingsCatalog;
import org.springframework.stereotype.Component;

import java.util.List;

import static com.huynqb.laundrylocker.common.settings.SettingDefinition.bool;
import static com.huynqb.laundrylocker.common.settings.SettingDefinition.integer;
import static com.huynqb.laundrylocker.common.settings.SettingDefinition.integerList;
import static com.huynqb.laundrylocker.common.settings.SettingDefinition.string;

/// Quy tắc nghiệp vụ của order-service admin chỉnh được (ADR-0005).
/// Key giữ nguyên tên property cũ ⇒ biến môi trường đang đặt trên VM vẫn là mặc định.
@Component
public class OrderSettingsCatalog implements SettingsCatalog {

    public static final String SEND_BASE_FEE = "app.order.send-base-fee";
    public static final String DRONE_DELIVERY_FEE = "app.order.drone-delivery-fee";
    public static final String RENTAL_RATE_STANDARD = "app.order.rental-rate-standard";
    public static final String RENTAL_RATE_XL = "app.order.rental-rate-xl";
    public static final String STORAGE_ITEM_FEE = "app.order.storage-item-fee";
    public static final String OVERTIME_FEE_PER_HOUR = "app.order.pickup-overtime-fee-per-hour";
    public static final String MAX_OVERTIME_FEE = "app.order.pickup-max-overtime-fee";
    public static final String MAX_OVERTIME_PERCENT = "app.order.pickup-max-overtime-percent";
    public static final String SEND_PICKUP_HOURS = "app.order.send-pickup-hours-limit";
    public static final String DRONE_PICKUP_HOURS = "app.order.drone-pickup-hours-limit";
    public static final String AUTO_CANCEL_HOURS = "app.order.auto-cancel-hours";
    public static final String OVERDUE_RELEASE_HOURS = "app.order.overdue-release-hours";
    public static final String REMINDER_COOLDOWN_MINUTES = "app.order.reminder-cooldown-minutes";
    public static final String RENTAL_MIN_HOURS = "app.order.rental-min-hours";
    public static final String RENTAL_MAX_HOURS = "app.order.rental-max-hours";
    public static final String RENTAL_DEFAULT_HOURS = "app.order.rental-default-hours";
    public static final String RENTAL_QUICK_HOURS = "app.order.rental-quick-hours";
    public static final String EXTEND_DEFAULT_HOURS = "app.order.extend-default-hours";
    public static final String EXTEND_MAX_HOURS = "app.order.extend-max-hours";
    public static final String REQUIRE_PAYMENT_BEFORE_DROP = "app.order.require-payment-before-drop";
    public static final String SEND_CONFIRM_REQUIRES_OPEN = "app.order.send-confirm-requires-open";
    public static final String BLOCK_UNPAID_RENTAL_ACCESS = "app.order.block-unpaid-rental-access";
    public static final String DRONE_DEMO_ENABLED = "app.drone.demo.enabled";
    public static final String DRONE_DEMO_ALLOWED_USER_IDS = "app.drone.demo.allowed-user-ids";
    public static final String DRONE_DEMO_STAGE_DELAY_MS = "app.drone.demo.stage-delay-ms";
    public static final String DRONE_MIN_PREFLIGHT_BATTERY = "app.order.drone-min-preflight-battery-percent";
    public static final String DRONE_DEFAULT_PARCEL_WEIGHT = "app.order.drone-default-parcel-weight-grams";
    public static final String DRONE_MAX_PAYLOAD_WEIGHT = "app.order.drone-max-payload-weight-grams";
    public static final String RECEIVER_NOTIFY_SMS = "app.order.receiver-notify-sms";
    public static final String RECEIVER_NOTIFY_EMAIL = "app.order.receiver-notify-email";

    private static final String PRICING = "Giá & phí";
    private static final String DEADLINES = "Thời hạn & tự động hoá";
    private static final String RENTAL = "Thuê tủ";
    private static final String PAYMENT = "Thanh toán";
    private static final String DRONE = "Giao hàng drone";
    private static final String RECEIVER = "Thông báo cho người nhận";

    @Override
    public List<SettingDefinition> definitions() {
        return List.of(
                integer(SEND_BASE_FEE, PRICING, "Phí gửi hàng qua tủ",
                        "Giá mỗi đơn gửi hàng (SEND). Áp dụng cho đơn tạo sau khi lưu.", 15000, 0, 100_000_000, "VND").asPublic(),
                integer(DRONE_DELIVERY_FEE, PRICING, "Phí giao hàng drone",
                        "Giá mỗi đơn giao bằng drone.", 15000, 0, 100_000_000, "VND").asPublic(),
                integer(RENTAL_RATE_STANDARD, PRICING, "Giá thuê ô tiêu chuẩn",
                        "Giá mỗi giờ thuê ô STANDARD (và mọi loại ô không phải XL).", 5000, 0, 10_000_000, "VND/giờ").asPublic(),
                integer(RENTAL_RATE_XL, PRICING, "Giá thuê ô XL",
                        "Giá mỗi giờ thuê ô cỡ XL.", 10000, 0, 10_000_000, "VND/giờ").asPublic(),
                integer(STORAGE_ITEM_FEE, PRICING, "Phí lưu trữ mỗi món",
                        "Đơn giá mỗi dòng hàng khi tạo đơn lưu trữ có danh sách món.", 5000, 0, 10_000_000, "VND").asPublic(),
                integer(OVERTIME_FEE_PER_HOUR, PRICING, "Phí quá hạn mỗi giờ",
                        "Tính theo số giờ trọn vẹn sau hạn lấy hàng.", 500, 0, 10_000_000, "VND/giờ").asPublic(),
                integer(MAX_OVERTIME_FEE, PRICING, "Trần phí quá hạn",
                        "Phí quá hạn không vượt số tiền này.", 50000, 0, 100_000_000, "VND").asPublic(),
                integer(MAX_OVERTIME_PERCENT, PRICING, "Trần phí quá hạn theo % giá đơn",
                        "Phí quá hạn không vượt tỉ lệ này của tổng tiền đơn.", 50, 0, 1000, "%").asPublic(),

                integer(SEND_PICKUP_HOURS, DEADLINES, "Hạn người nhận lấy hàng gửi",
                        "Tính từ lúc người gửi bỏ hàng vào ô.", 48, 1, 720, "giờ").asPublic(),
                integer(DRONE_PICKUP_HOURS, DEADLINES, "Hạn lấy hàng giao bằng drone",
                        "Tính từ lúc drone thả hàng vào ô.", 24, 1, 720, "giờ").asPublic(),
                integer(AUTO_CANCEL_HOURS, DEADLINES, "Tự huỷ đơn chưa bỏ hàng sau",
                        "Đơn INITIALIZED không xác nhận bỏ hàng sẽ bị huỷ và nhả ô. Nên ≤ thời gian giữ ô RESERVED bên locker.", 24, 1, 720, "giờ"),
                integer(OVERDUE_RELEASE_HOURS, DEADLINES, "Nhả ô quá hạn sau",
                        "Số giờ sau hạn lấy hàng thì đơn chuyển EXPIRED và nhả ô. 0 = không tự nhả.", 24, 0, 720, "giờ"),
                integer(REMINDER_COOLDOWN_MINUTES, DEADLINES, "Khoảng cách nhắc quá hạn",
                        "Không gửi nhắc lấy hàng cho cùng một đơn dày hơn khoảng này.", 60, 5, 10_080, "phút"),

                integer(RENTAL_MIN_HOURS, RENTAL, "Số giờ thuê tối thiểu", "", 1, 1, 720, "giờ").asPublic(),
                integer(RENTAL_MAX_HOURS, RENTAL, "Số giờ thuê tối đa", "", 720, 1, 8760, "giờ").asPublic(),
                integer(RENTAL_DEFAULT_HOURS, RENTAL, "Số giờ thuê mặc định trên app", "", 4, 1, 8760, "giờ").asPublic(),
                integerList(RENTAL_QUICK_HOURS, RENTAL, "Nút chọn nhanh số giờ thuê",
                        "Danh sách giờ hiển thị trên app, phân tách bằng dấu phẩy.", "2,4,8,12,24", 1, 8760, "giờ").asPublic(),
                integer(EXTEND_DEFAULT_HOURS, RENTAL, "Số giờ gia hạn mặc định trên app", "", 2, 1, 8760, "giờ").asPublic(),
                integer(EXTEND_MAX_HOURS, RENTAL, "Số giờ gia hạn tối đa mỗi lần", "", 24, 1, 8760, "giờ").asPublic(),

                bool(REQUIRE_PAYMENT_BEFORE_DROP, PAYMENT, "Bắt buộc thanh toán trước khi bỏ hàng",
                        "Chặn mở ô bỏ hàng, xác nhận bỏ hàng và kết thúc thuê khi đơn chưa thanh toán.",
                        true).asPublic(),
                bool(SEND_CONFIRM_REQUIRES_OPEN, PAYMENT, "Chỉ xác nhận bỏ hàng sau khi đã mở ô",
                        "Đơn gửi hàng chỉ được xác nhận đã bỏ hàng khi ô đã từng được mở bằng mã gửi.", true),
                bool(BLOCK_UNPAID_RENTAL_ACCESS, RENTAL, "Chặn mở ô thuê khi còn nợ tiền gia hạn",
                        "Gia hạn làm đơn thuê chưa thanh toán; bật để PIN không mở được ô cho tới khi trả.",
                        false),

                bool(RECEIVER_NOTIFY_SMS, RECEIVER, "Gửi mã mở tủ qua SMS",
                        "Nhắn mã và hạn lấy hàng tới số điện thoại người nhận, kể cả người chưa có "
                                + "tài khoản. Cần khoá nhà cung cấp SMS trên máy chủ; chưa có khoá thì "
                                + "bật cũng không gửi được.", true),
                bool(RECEIVER_NOTIFY_EMAIL, RECEIVER, "Gửi mã mở tủ qua email",
                        "Gửi mã tới email người nhận khi người gửi có nhập email.", true),

                bool(DRONE_DEMO_ENABLED, DRONE, "Cho phép chế độ drone DEMO",
                        "Bật bộ giả lập chặng bay cho đơn drone.", true),
                string(DRONE_DEMO_ALLOWED_USER_IDS, DRONE, "User được dùng drone DEMO",
                        "Danh sách user id cách nhau bởi dấu phẩy. Để trống = mọi người.", "", List.of()),
                integer(DRONE_DEMO_STAGE_DELAY_MS, DRONE, "Thời gian mỗi chặng bay giả lập",
                        "", 3000, 1000, 600_000, "ms"),
                integer(DRONE_MIN_PREFLIGHT_BATTERY, DRONE, "Pin tối thiểu để nhận đơn drone",
                        "Drone có pin ≤ ngưỡng này không được nhận đơn.", 20, 0, 100, "%"),
                integer(DRONE_DEFAULT_PARCEL_WEIGHT, DRONE, "Khối lượng kiện mặc định trên app",
                        "", 1200, 1, 100_000, "gram").asPublic(),
                integer(DRONE_MAX_PAYLOAD_WEIGHT, DRONE, "Khối lượng tải tối đa của drone",
                        "Chặn xác nhận nạp hàng nếu kiện vượt tải vận hành cho phép.",
                        5000, 1, 100_000, "gram"));
    }
}

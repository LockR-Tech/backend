package com.huynqb.laundrylocker.order.settings;

import com.huynqb.laundrylocker.common.settings.SettingDefinition;
import com.huynqb.laundrylocker.common.settings.SettingsCatalog;
import org.springframework.stereotype.Component;

import java.util.List;

import static com.huynqb.laundrylocker.common.settings.SettingDefinition.bool;
import static com.huynqb.laundrylocker.common.settings.SettingDefinition.decimal;
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
    public static final String RENTAL_DAILY_RATE_STANDARD = "app.order.rental-daily-rate-standard";
    public static final String RENTAL_DAILY_RATE_XL = "app.order.rental-daily-rate-xl";
    public static final String STORAGE_ITEM_FEE = "app.order.storage-item-fee";
    public static final String OVERTIME_FEE_PER_HOUR = "app.order.pickup-overtime-fee-per-hour";
    public static final String MAX_OVERTIME_FEE = "app.order.pickup-max-overtime-fee";
    public static final String MAX_OVERTIME_PERCENT = "app.order.pickup-max-overtime-percent";
    public static final String RENTAL_OVERTIME_RATE_PERCENT = "app.order.rental-overtime-rate-percent";
    public static final String SEND_PICKUP_HOURS = "app.order.send-pickup-hours-limit";
    public static final String DRONE_PICKUP_HOURS = "app.order.drone-pickup-hours-limit";
    public static final String AUTO_CANCEL_HOURS = "app.order.auto-cancel-hours";
    public static final String DRONE_UNPAID_CANCEL_MINUTES = "app.order.drone-unpaid-cancel-minutes";
    public static final String DRONE_DISPATCH_TIMEOUT_MINUTES = "app.order.drone-dispatch-timeout-minutes";
    public static final String DRONE_SURCHARGE_TIMEOUT_MINUTES = "app.order.drone-surcharge-timeout-minutes";
    public static final String DRONE_PICKUP_OFFER_SECONDS = "app.order.drone-pickup-offer-seconds";
    public static final String DRONE_PICKUP_TIMEOUT_MINUTES = "app.order.drone-pickup-timeout-minutes";
    public static final String DRONE_LAUNCH_TIMEOUT_MINUTES = "app.order.drone-launch-timeout-minutes";
    public static final String DRONE_BLOCKED_TIMEOUT_MINUTES = "app.order.drone-blocked-timeout-minutes";
    public static final String DRONE_MAX_PICKUP_METERS = "app.order.drone-max-pickup-meters";
    public static final String DRONE_TECHNICIAN_LOCATION_MAX_AGE_MINUTES =
            "app.order.drone-technician-location-max-age-minutes";
    public static final String DRONE_FLIGHTS_SUSPENDED = "app.order.drone-flights-suspended";
    public static final String DRONE_FLIGHT_START_HOUR = "app.order.drone-flight-start-hour";
    public static final String DRONE_FLIGHT_END_HOUR = "app.order.drone-flight-end-hour";
    public static final String DRONE_MAX_ROUTE_METERS = "app.order.drone-max-route-meters";
    public static final String DRONE_MAX_OPEN_ORDERS = "app.order.drone-max-open-orders-per-user";
    public static final String DRONE_MAX_PARCEL_LENGTH = "app.order.drone-max-parcel-length-cm";
    public static final String DRONE_MAX_PARCEL_WIDTH = "app.order.drone-max-parcel-width-cm";
    public static final String DRONE_MAX_PARCEL_HEIGHT = "app.order.drone-max-parcel-height-cm";
    public static final String DRONE_MAX_DECLARED_VALUE = "app.order.drone-max-declared-value";
    public static final String AUTO_CANCEL_UNPAID_MINUTES = "app.order.auto-cancel-unpaid-minutes";
    public static final String OVERDUE_RELEASE_HOURS = "app.order.overdue-release-hours";
    public static final String OVERTIME_GRACE_PERIOD_MINUTES = "app.order.overtime-grace-period-minutes";
    public static final String BOX_INCIDENT_PICKUP_GRACE_HOURS = "app.order.box-incident-pickup-grace-hours";
    public static final String RENTAL_AUTO_RELEASE_ENABLED = "app.order.rental-auto-release-enabled";
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
    public static final String DRONE_DEMO_INDOOR_ARUCO_ENABLED = "app.drone.demo.indoor-aruco-enabled";
    public static final String DRONE_DEMO_INDOOR_HANDHELD_ENABLED = "app.drone.demo.indoor-handheld-enabled";
    public static final String DRONE_DEMO_HOLD_OPEN_SECONDS = "app.drone.demo.hold-open-seconds";
    public static final String DRONE_OPEN_LID_MAX_WAIT_SECONDS = "app.drone.delivery.open-lid-max-wait-seconds";
    public static final String DRONE_MIN_PREFLIGHT_BATTERY = "app.order.drone-min-preflight-battery-percent";
    public static final String DRONE_DEFAULT_PARCEL_WEIGHT = "app.order.drone-default-parcel-weight-grams";
    public static final String DRONE_MAX_PAYLOAD_WEIGHT = "app.order.drone-max-payload-weight-grams";
    public static final String DRONE_BASE_WEIGHT = "app.order.drone-base-weight-grams";
    public static final String DRONE_WEIGHT_STEP = "app.order.drone-weight-step-grams";
    public static final String DRONE_WEIGHT_STEP_FEE = "app.order.drone-weight-step-fee";
    public static final String DRONE_WEIGHT_OPTIONS = "app.order.drone-weight-options-grams";
    public static final String DRONE_WEIGHT_TOLERANCE = "app.order.drone-weight-tolerance-grams";
    public static final String RECEIVER_NOTIFY_SMS = "app.order.receiver-notify-sms";
    public static final String RECEIVER_NOTIFY_EMAIL = "app.order.receiver-notify-email";
    public static final String INCIDENT_POLICY_ENABLED = "app.order.incident-policy-enabled";
    public static final String INCIDENT_COMPENSATION_RATE = "app.order.incident-compensation-rate";
    public static final String INCIDENT_COMPENSATION_CAP = "app.order.incident-compensation-cap";
    public static final String INCIDENT_FREE_REDELIVERY = "app.order.incident-free-redelivery";
    public static final String INCIDENT_REFUND_SHIPPING_FEE = "app.order.incident-refund-shipping-fee";
    public static final String INCIDENT_RECOVERY_SLA_HOURS = "app.order.incident-recovery-sla-hours";
    public static final String INCIDENT_APPROVAL_REQUIRED = "app.order.incident-approval-required";
    public static final String INCIDENT_DISPUTE_ALLOWED = "app.order.incident-dispute-allowed";
    public static final String MAX_VOUCHERS_PER_ORDER = "app.order.max-vouchers-per-order";
    public static final String DRONE_RETURN_MIN_BATTERY = "app.order.drone-return-min-battery-percent";
    public static final String DRONE_PICKUP_REOFFER_COOLDOWN_MINUTES = "app.order.drone-pickup-reoffer-cooldown-minutes";
    public static final String DRONE_PICKUP_CONFIRM_MAX_DISTANCE = "app.order.drone-pickup-confirm-max-distance-meters";
    // Key thời tiết, nắp tủ, telemetry giữ tên property cũ (trước chỉ đặt được bằng biến môi trường).
    public static final String WEATHER_MAX_WIND_SPEED = "app.drone.weather.max-wind-speed-kmh";
    public static final String WEATHER_MAX_WIND_GUST = "app.drone.weather.max-wind-gust-kmh";
    public static final String WEATHER_MIN_VISIBILITY = "app.drone.weather.min-visibility-meters";
    public static final String WEATHER_MAX_PRECIPITATION = "app.drone.weather.max-precipitation-mm";
    public static final String WEATHER_BLOCK_PRECIPITATION_CODES = "app.drone.weather.block-precipitation-codes";
    public static final String WEATHER_WINDOW_MINUTES = "app.drone.weather.forecast-window-minutes";
    public static final String WEATHER_BLOCK_WHEN_UNAVAILABLE = "app.drone.weather.block-when-unavailable";
    public static final String WEATHER_AUTO_STATION_PAUSE = "app.drone.weather.auto-station-pause";
    public static final String LID_ACK_TIMEOUT_SECONDS = "app.drone.delivery.lid-ack-timeout-seconds";
    public static final String LID_MOTION_TIMEOUT_SECONDS = "app.drone.delivery.lid-motion-timeout-seconds";
    public static final String LID_MAX_RETRIES = "app.drone.delivery.lid-max-retries";
    public static final String LOCKER_OFFLINE_AFTER_SECONDS = "app.drone.delivery.locker-offline-after-seconds";
    public static final String TELEMETRY_DEPARTED_DISTANCE = "app.drone.telemetry.departed-distance-m";
    public static final String TELEMETRY_APPROACH_DISTANCE = "app.drone.telemetry.approach-distance-m";
    public static final String TELEMETRY_ARRIVAL_RADIUS = "app.drone.telemetry.arrival-radius-m";

    private static final String PRICING = "Giá & phí";
    private static final String DEADLINES = "Thời hạn & tự động hoá";
    private static final String RENTAL = "Thuê tủ";
    private static final String PAYMENT = "Thanh toán";
    private static final String DRONE = "Giao hàng drone";
    private static final String DRONE_DEMO = "Mô phỏng & Thử nghiệm Drone";
    private static final String RECEIVER = "Thông báo cho người nhận";
    private static final String INCIDENT = "Sự cố & bồi thường";
    private static final String VOUCHER = "Mã giảm giá";
    private static final String WEATHER = "Thời tiết & điều kiện bay";
    private static final String DRONE_LINK = "Kết nối tủ & định vị drone";

    @Override
    public List<SettingDefinition> definitions() {
        return List.of(
                integer(SEND_BASE_FEE, PRICING, "Phí gửi hàng qua tủ",
                        "Giá mỗi đơn gửi hàng (SEND). Áp dụng cho đơn tạo sau khi lưu.", 15000, 0, 100_000_000, "VND").asPublic(),
                integer(DRONE_DELIVERY_FEE, PRICING, "Phí giao hàng drone",
                        "Giá cơ bản mỗi đơn giao bằng drone, đã gồm khối lượng cơ bản.", 15000, 0, 100_000_000, "VND").asPublic(),
                integer(DRONE_WEIGHT_STEP_FEE, PRICING, "Phụ phí drone mỗi nấc khối lượng",
                        "Cộng thêm cho mỗi nấc khối lượng (kể cả nấc chưa trọn) vượt khối lượng cơ bản. 0 = giá đồng nhất.",
                        3000, 0, 10_000_000, "VND").asPublic(),
                integer(RENTAL_RATE_STANDARD, PRICING, "Giá thuê ô tiêu chuẩn",
                        "Giá mỗi giờ thuê ô STANDARD (và mọi loại ô không phải XL).", 5000, 0, 10_000_000, "VND/giờ").asPublic(),
                integer(RENTAL_RATE_XL, PRICING, "Giá thuê ô XL",
                        "Giá mỗi giờ thuê ô cỡ XL.", 10000, 0, 10_000_000, "VND/giờ").asPublic(),
                integer(RENTAL_DAILY_RATE_STANDARD, PRICING, "Giá thuê ô tiêu chuẩn theo ngày",
                        "Giá mỗi 24 giờ thuê ô STANDARD, cũng là trần của phần giờ lẻ trong ngày: thuê 30 giờ = "
                                + "1 ngày + min(6 × giá giờ, giá ngày). 0 = không có giá ngày, chỉ tính theo giờ.",
                        40000, 0, 100_000_000, "VND/ngày").asPublic(),
                integer(RENTAL_DAILY_RATE_XL, PRICING, "Giá thuê ô XL theo ngày",
                        "Như giá ngày ô tiêu chuẩn, cho ô cỡ XL. 0 = chỉ tính theo giờ.",
                        80000, 0, 100_000_000, "VND/ngày").asPublic(),
                integer(STORAGE_ITEM_FEE, PRICING, "Phí lưu trữ mỗi món",
                        "Đơn giá mỗi dòng hàng khi tạo đơn lưu trữ có danh sách món.", 5000, 0, 10_000_000, "VND").asPublic(),
                integer(OVERTIME_FEE_PER_HOUR, PRICING, "Phí quá hạn mỗi giờ",
                        "Đơn gửi hàng: tính theo số giờ trọn vẹn sau hạn lấy hàng.", 500, 0, 10_000_000, "VND/giờ").asPublic(),
                integer(MAX_OVERTIME_FEE, PRICING, "Trần phí quá hạn",
                        "Đơn gửi hàng: phí quá hạn không vượt số tiền này.", 50000, 0, 100_000_000, "VND").asPublic(),
                integer(MAX_OVERTIME_PERCENT, PRICING, "Trần phí quá hạn theo % giá đơn",
                        "Đơn gửi hàng: phí quá hạn không vượt tỉ lệ này của giá đơn (không tính phí quá hạn đã cộng).",
                        50, 0, 1000, "%").asPublic(),

                integer(SEND_PICKUP_HOURS, DEADLINES, "Hạn người nhận lấy hàng gửi",
                        "Tính từ lúc người gửi bỏ hàng vào ô.", 48, 1, 720, "giờ").asPublic(),
                integer(DRONE_PICKUP_HOURS, DEADLINES, "Hạn lấy hàng giao bằng drone",
                        "Tính từ lúc drone thả hàng vào ô.", 24, 1, 720, "giờ").asPublic(),
                integer(AUTO_CANCEL_HOURS, DEADLINES, "Tự huỷ đơn chưa bỏ hàng sau",
                        "Đơn INITIALIZED không xác nhận bỏ hàng sẽ bị huỷ và nhả ô. Nên ≤ thời gian giữ ô RESERVED bên locker.", 24, 1, 720, "giờ"),
                integer(DRONE_UNPAID_CANCEL_MINUTES, DEADLINES, "Tự huỷ đơn drone chưa thanh toán sau",
                        "Đơn drone chưa trả tiền vẫn giữ ô ở tủ nhận; quá thời gian này thì huỷ và nhả ô. "
                                + "0 = không tự huỷ.", 30, 0, 10_080, "phút").asPublic(),
                integer(DRONE_DISPATCH_TIMEOUT_MINUTES, DEADLINES, "Tự đóng đơn drone đã trả tiền mà không có KTV nhận lấy hàng sau",
                        "Tính từ lúc thanh toán: không kỹ thuật viên drone nào nhận lấy hàng thì đơn kết thúc "
                                + "\"giao không hoàn tất\" và được hoàn tiền. 0 = không tự đóng.",
                        120, 0, 10_080, "phút").asPublic(),
                integer(DRONE_PICKUP_OFFER_SECONDS, DEADLINES, "Thời gian KTV drone trả lời lời mời lấy hàng",
                        "Quá thời gian này mà KTV chưa nhận thì hệ thống mời người rảnh gần kế tiếp.",
                        180, 30, 3600, "giây").asPublic(),
                integer(DRONE_PICKUP_TIMEOUT_MINUTES, DEADLINES, "Hạn KTV drone tới lấy hàng sau khi nhận việc",
                        "Quá hạn mà chưa xác nhận đã lấy hàng thì việc được chuyển cho KTV khác. 0 = không giới hạn.",
                        60, 0, 1440, "phút"),
                integer(DRONE_LAUNCH_TIMEOUT_MINUTES, DEADLINES, "Hạn phóng drone sau khi đã lấy hàng",
                        "Quá hạn mà drone chưa cất cánh thì đơn bị đánh dấu đang gặp sự cố để điều phối và admin xử lý. "
                                + "0 = không giới hạn.", 120, 0, 1440, "phút"),
                integer(DRONE_BLOCKED_TIMEOUT_MINUTES, DEADLINES, "Tự đóng đơn drone đang gặp sự cố sau",
                        "Đơn đã thanh toán nhưng không phục vụ được (phiếu sự cố drone/tủ/bãi đáp, tạm dừng bay). "
                                + "Quá thời gian này mà chưa hết sự cố và chưa ai xử lý thì đơn kết thúc "
                                + "\"giao không hoàn tất\" và được hoàn tiền. 0 = chỉ đóng khi có người xác nhận.",
                        30, 0, 10_080, "phút").asPublic(),
                integer(DRONE_SURCHARGE_TIMEOUT_MINUTES, DEADLINES, "Tự huỷ đơn drone nợ phụ thu cân lệch sau",
                        "Tính từ lúc nạp hàng. Quá hạn thì huỷ, nhả drone, hoàn phần đã trả và trả kiện cho người gửi. "
                                + "0 = không tự huỷ.", 60, 0, 10_080, "phút").asPublic(),
                integer(AUTO_CANCEL_UNPAID_MINUTES, DEADLINES, "Tự huỷ đơn chưa thanh toán sau",
                        "Đơn INITIALIZED chưa thanh toán sẽ tự động bị huỷ và giải phóng ô tủ sau số phút này nếu khách không hoàn tất thanh toán.",
                        15, 1, 180, "phút").asPublic(),
                integer(OVERDUE_RELEASE_HOURS, DEADLINES, "Nhả ô quá hạn sau",
                        "Số giờ sau hạn lấy hàng thì đơn chuyển EXPIRED và nhả ô. 0 = không tự nhả (tiếp tục lưu giữ tại tủ và tính phí quá hạn).", 72, 0, 720, "giờ").asPublic(),
                integer(BOX_INCIDENT_PICKUP_GRACE_HOURS, DEADLINES, "Thời gian lấy đồ thêm sau khi xử lý sự cố ô",
                        "Ô của đơn đang có hàng bị báo sự cố: phí quá giờ dừng trong lúc chờ KTV. Xử lý xong, hạn lấy "
                                + "đồ được cộng bù đúng thời gian bị kẹt và thêm số giờ này để khách kịp tới lấy.",
                        2, 0, 72, "giờ").asPublic(),
                integer(OVERTIME_GRACE_PERIOD_MINUTES, DEADLINES, "Thời gian ân hạn phí quá giờ khi thanh toán",
                        "Sau khi khách nộp phí quá hạn, hệ thống đóng băng phí trong số phút này để khách mở tủ lấy đồ mà không bị phát sinh thêm tiền. 0 = không ân hạn.", 15, 0, 180, "phút").asPublic(),
                integer(REMINDER_COOLDOWN_MINUTES, DEADLINES, "Khoảng cách nhắc quá hạn",
                        "Không gửi nhắc lấy hàng cho cùng một đơn dày hơn khoảng này.", 60, 5, 10_080, "phút"),

                bool(RENTAL_AUTO_RELEASE_ENABLED, RENTAL, "Tự động dời hàng thuê tủ về kho khi quá hạn",
                        "Bật để đơn thuê tủ quá hạn bị dời về kho sau 'Nhả ô quá hạn sau' giờ. Tắt (mặc định) để đồ luôn ở trong tủ và tiếp tục tính phí thuê/quá giờ đến khi khách tự lấy đồ.", false).asPublic(),
                integer(RENTAL_MIN_HOURS, RENTAL, "Số giờ thuê tối thiểu", "", 1, 1, 720, "giờ").asPublic(),
                integer(RENTAL_MAX_HOURS, RENTAL, "Số giờ thuê tối đa", "", 720, 1, 8760, "giờ").asPublic(),
                integer(RENTAL_DEFAULT_HOURS, RENTAL, "Số giờ thuê mặc định trên app", "", 4, 1, 8760, "giờ").asPublic(),
                integerList(RENTAL_QUICK_HOURS, RENTAL, "Nút chọn nhanh số giờ thuê",
                        "Danh sách giờ hiển thị trên app, phân tách bằng dấu phẩy.", "2,4,8,12,24", 1, 8760, "giờ").asPublic(),
                integer(EXTEND_DEFAULT_HOURS, RENTAL, "Số giờ gia hạn mặc định trên app", "", 2, 1, 8760, "giờ").asPublic(),
                integer(EXTEND_MAX_HOURS, RENTAL, "Số giờ gia hạn tối đa mỗi lần", "", 24, 1, 8760, "giờ").asPublic(),
                integer(RENTAL_OVERTIME_RATE_PERCENT, RENTAL, "Phí ở quá giờ thuê (theo % giá thuê)",
                        "Mỗi giờ ở quá hạn thuê (giờ đã bắt đầu tính trọn giờ) = giá thuê mỗi giờ của loại ô × tỉ lệ "
                                + "này. Tối thiểu 100% để ở quá giờ không rẻ hơn gia hạn; không áp trần phí quá hạn "
                                + "của đơn gửi hàng.", 100, 100, 1000, "%").asPublic(),

                integer(MAX_VOUCHERS_PER_ORDER, VOUCHER, "Số mã giảm giá tối đa mỗi đơn",
                        "Số mã giảm giá khách được áp vào một đơn (gửi hàng, thuê ô, giao drone). 0 = tạm ngưng "
                                + "mọi mã giảm giá. Lớn hơn 1 thì chỉ các mã đánh dấu \"dùng kèm được\" mới cộng dồn "
                                + "với nhau. Điểm thưởng luôn dùng được thêm, không tính vào số mã.",
                        1, 0, 5, "mã").asPublic(),

                bool(REQUIRE_PAYMENT_BEFORE_DROP, PAYMENT, "Bắt buộc thanh toán trước khi bỏ hàng",
                        "Chặn mở ô bỏ hàng, xác nhận bỏ hàng và kết thúc thuê khi đơn chưa thanh toán.",
                        true).asPublic(),
                bool(SEND_CONFIRM_REQUIRES_OPEN, PAYMENT, "Chỉ xác nhận bỏ hàng sau khi đã mở ô",
                        "Đơn gửi hàng chỉ được xác nhận đã bỏ hàng khi ô đã từng được mở bằng mã gửi.", true),
                bool(BLOCK_UNPAID_RENTAL_ACCESS, RENTAL, "Chặn mở ô thuê khi còn nợ tiền gia hạn",
                        "Gia hạn làm đơn thuê chưa thanh toán; bật để PIN không mở được ô cho tới khi trả.",
                        false).asPublic(),

                bool(RECEIVER_NOTIFY_SMS, RECEIVER, "Gửi mã mở tủ qua SMS",
                        "Nhắn mã và hạn lấy hàng tới số điện thoại người nhận, kể cả người chưa có "
                                + "tài khoản. Cần khoá nhà cung cấp SMS trên máy chủ; chưa có khoá thì "
                                + "bật cũng không gửi được.", true),
                bool(RECEIVER_NOTIFY_EMAIL, RECEIVER, "Gửi mã mở tủ qua email",
                        "Gửi mã tới email người nhận khi người gửi có nhập email.", true),

                bool(INCIDENT_POLICY_ENABLED, INCIDENT, "Bật chính sách xử lý sự cố rơi kiện",
                        "Cho phép đề xuất giao lại hoặc bồi thường sau khi Admin đã xác minh sự cố.", true),
                decimal(INCIDENT_COMPENSATION_RATE, INCIDENT, "Tỷ lệ bồi thường đề xuất",
                        "Tỷ lệ mặc định trên giá trị đủ điều kiện; đây không phải mức pháp lý bắt buộc.",
                        "60", "0", "100", "%"),
                decimal(INCIDENT_COMPENSATION_CAP, INCIDENT, "Trần bồi thường",
                        "0 = không đặt trần; Admin vẫn phải thẩm định giá trị khai báo và điều khoản.",
                        "0", "0", "1000000000", "VND"),
                bool(INCIDENT_FREE_REDELIVERY, INCIDENT, "Cho phép giao lại miễn phí",
                        "Chỉ áp dụng khi kiện đã về Hub/kho và drone đã được xác nhận an toàn.", true),
                bool(INCIDENT_REFUND_SHIPPING_FEE, INCIDENT, "Hoàn phí giao hàng khi bồi thường",
                        "Phân biệt với khoản bồi thường giá trị kiện hàng.", true),
                integer(INCIDENT_RECOVERY_SLA_HOURS, INCIDENT, "SLA thu hồi kiện",
                        "Thời gian mục tiêu từ lúc báo rơi tới khi KTV gửi kết quả tìm kiếm.", 4, 1, 720, "giờ"),
                bool(INCIDENT_APPROVAL_REQUIRED, INCIDENT, "Bắt buộc duyệt bồi thường",
                        "Khoản bồi thường chỉ được thanh toán sau khi Admin duyệt.", true),
                bool(INCIDENT_DISPUTE_ALLOWED, INCIDENT, "Cho phép khách yêu cầu xem xét lại",
                        "Giữ proposal cũ và tạo phiên bản mới thay vì ghi đè lịch sử.", true),

                // === GIAO HÀNG DRONE (VẬN HÀNH) ===
                integer(DRONE_MIN_PREFLIGHT_BATTERY, DRONE, "Pin tối thiểu để nhận đơn drone",
                        "Drone có pin ≤ ngưỡng này không được nhận đơn.", 20, 0, 100, "%"),
                integer(DRONE_DEFAULT_PARCEL_WEIGHT, DRONE, "Khối lượng kiện mặc định trên app",
                        "", 1200, 1, 100_000, "gram").asPublic(),
                integer(DRONE_MAX_PAYLOAD_WEIGHT, DRONE, "Khối lượng tải tối đa của drone",
                        "Chặn đặt đơn và xác nhận nạp hàng nếu kiện vượt tải vận hành cho phép.",
                        5000, 1, 100_000, "gram").asPublic(),
                integer(DRONE_BASE_WEIGHT, DRONE, "Khối lượng cơ bản của phí drone",
                        "Kiện tới mức này chỉ trả phí giao hàng drone cơ bản.", 500, 1, 100_000, "gram").asPublic(),
                integer(DRONE_WEIGHT_STEP, DRONE, "Nấc khối lượng tính phụ phí drone",
                        "Mỗi nấc vượt khối lượng cơ bản cộng một lần phụ phí.", 250, 1, 100_000, "gram").asPublic(),
                integerList(DRONE_WEIGHT_OPTIONS, DRONE, "Các mức khối lượng khách chọn khi đặt drone",
                        "Danh sách gram hiển thị trên app, phân tách bằng dấu phẩy. Mức vượt tải tối đa bị ẩn.",
                        "500,1000,2000,3000,4000,5000", 1, 100_000, "gram").asPublic(),
                integer(DRONE_WEIGHT_TOLERANCE, DRONE, "Sai số cân cho phép khi nạp hàng drone",
                        "Cân thực tế vượt khối lượng khai báo không quá mức này thì không thu thêm.",
                        50, 0, 10_000, "gram").asPublic(),
                bool(DRONE_FLIGHTS_SUSPENDED, DRONE, "Tạm dừng bay",
                        "Bật khi thời tiết xấu hoặc có sự cố vận hành: không nhận đơn drone mới, đội bay không "
                                + "tiếp nhận và không phóng được.", false).asPublic(),
                integer(DRONE_FLIGHT_START_HOUR, DRONE, "Giờ bắt đầu được phóng drone",
                        "Giờ Việt Nam. Đặt 0 và 24 để không giới hạn khung giờ bay.", 0, 0, 23, "giờ").asPublic(),
                integer(DRONE_FLIGHT_END_HOUR, DRONE, "Giờ ngừng phóng drone",
                        "Sau giờ này (giờ Việt Nam) đội bay không phóng được.", 24, 1, 24, "giờ").asPublic(),
                integer(DRONE_MAX_ROUTE_METERS, DRONE, "Tầm bay tối đa giữa hai tủ",
                        "Khoảng cách đường chim bay tủ gửi → tủ nhận vượt mức này thì không đặt được đơn. "
                                + "0 = không giới hạn.", 5000, 0, 100_000, "mét").asPublic(),
                integer(DRONE_MAX_PICKUP_METERS, DRONE, "Bán kính lấy hàng tận nhà",
                        "Điểm lấy hàng phải cách một tủ có bãi đáp không quá mức này (đường chim bay).",
                        5000, 100, 100_000, "mét").asPublic(),
                integer(DRONE_TECHNICIAN_LOCATION_MAX_AGE_MINUTES, DRONE, "Vị trí KTV drone còn hiệu lực trong",
                        "KTV đang trong ca nhưng app không gửi vị trí quá thời gian này thì không được mời lấy hàng.",
                        10, 1, 720, "phút"),
                integer(DRONE_MAX_OPEN_ORDERS, DRONE, "Số đơn drone đang mở tối đa mỗi khách",
                        "Mỗi đơn drone chưa giao xong giữ ô ở hai tủ. 0 = không giới hạn.", 3, 0, 100, "đơn").asPublic(),
                integer(DRONE_MAX_PARCEL_LENGTH, DRONE, "Chiều dài kiện tối đa", "Theo khoang hàng của drone.",
                        30, 1, 500, "cm").asPublic(),
                integer(DRONE_MAX_PARCEL_WIDTH, DRONE, "Chiều rộng kiện tối đa", "", 25, 1, 500, "cm").asPublic(),
                integer(DRONE_MAX_PARCEL_HEIGHT, DRONE, "Chiều cao kiện tối đa", "", 20, 1, 500, "cm").asPublic(),
                integer(DRONE_MAX_DECLARED_VALUE, DRONE, "Giá trị khai báo tối đa của kiện drone",
                        "Kiện có giá trị khai báo cao hơn không được nhận.", 2_000_000, 0, 1_000_000_000, "VND")
                        .asPublic(),
                integer(DRONE_RETURN_MIN_BATTERY, DRONE, "Pin tối thiểu để ra lệnh drone quay về",
                        "Khi báo rơi kiện, KTV chỉ yêu cầu drone đang bay quay về tủ gửi nếu pin còn từ mức này trở lên; "
                                + "thấp hơn thì drone tự hạ cánh an toàn theo autopilot.", 15, 0, 100, "%"),
                integer(DRONE_PICKUP_REOFFER_COOLDOWN_MINUTES, DRONE, "Không mời lại KTV vừa từ chối trong",
                        "KTV vừa từ chối hoặc để quá hạn lời mời lấy hàng tận nhà không được mời lại cùng đơn trong "
                                + "khoảng này. 0 = mời lại ngay.", 10, 0, 240, "phút"),
                integer(DRONE_PICKUP_CONFIRM_MAX_DISTANCE, DRONE, "KTV phải ở gần điểm lấy hàng khi xác nhận đã lấy",
                        "Theo GPS ca trực (cập nhật trong hạn \"vị trí KTV còn hiệu lực\"): xa hơn mức này thì không cho "
                                + "bấm \"Đã lấy hàng\". 0 = không kiểm (chỉ dùng khi thử nghiệm tại bàn).", 300, 0, 50000, "mét"),

                // === THỜI TIẾT & ĐIỀU KIỆN BAY ===
                // Chỉnh theo khung drone thật và giấy phép bay. Áp cho đặt đơn, đội bay phóng drone và bảng thời tiết.
                integer(WEATHER_MAX_WIND_SPEED, WEATHER, "Tốc độ gió tối đa",
                        "Dự báo gió (10 m) trên tuyến vượt mức này thì không bay.", 30, 1, 150, "km/h"),
                integer(WEATHER_MAX_WIND_GUST, WEATHER, "Gió giật tối đa",
                        "Dự báo gió giật trên tuyến vượt mức này thì không bay. Nguồn dự phòng MET Norway không có gió "
                                + "giật ở Việt Nam nên ước tính bằng 1,5 × tốc độ gió.", 40, 1, 200, "km/h"),
                integer(WEATHER_MIN_VISIBILITY, WEATHER, "Tầm nhìn tối thiểu",
                        "Tầm nhìn dự báo thấp hơn mức này (sương mù, mưa dày) thì không bay. 0 = không xét tầm nhìn.",
                        1500, 0, 50_000, "mét"),
                decimal(WEATHER_MAX_PRECIPITATION, WEATHER, "Lượng mưa tối đa",
                        "Lượng mưa dự báo mỗi 15 phút trên tuyến vượt mức này thì không bay. 0 = có mưa là không bay.",
                        "0", "0", "50", "mm"),
                bool(WEATHER_BLOCK_PRECIPITATION_CODES, WEATHER, "Chặn khi dự báo có mưa hoặc dông",
                        "Bật: dự báo mưa phùn, mưa, mưa rào, dông (mã WMO 51–99) là không bay dù lượng mưa dưới ngưỡng. "
                                + "Tắt: chỉ xét lượng mưa.", true),
                integer(WEATHER_WINDOW_MINUTES, WEATHER, "Xét dự báo trong",
                        "Khoảng thời gian dự báo được xét tính từ lúc kiểm tra — nên ≥ thời gian một chuyến bay.",
                        30, 15, 120, "phút"),
                bool(WEATHER_BLOCK_WHEN_UNAVAILABLE, WEATHER, "Chặn bay khi không có dữ liệu thời tiết",
                        "Bật (khuyến nghị): cả Open-Meteo lẫn MET Norway đều không trả lời thì không đặt đơn, không phóng "
                                + "drone. Tắt: vẫn bay khi không biết trời — chỉ dùng khi đội bay tự quan sát thời tiết.", true),
                bool(WEATHER_AUTO_STATION_PAUSE, WEATHER, "Tự dừng trạm khi dự báo xấu",
                        "Bật: mỗi 5 phút hệ thống xét dự báo tại từng trạm có bãi đáp; trạm vượt ngưỡng thì tự tạm dừng "
                                + "(lý do Thời tiết, ghi vào lịch sử dừng trạm) và tự mở lại khi trời đủ điều kiện. "
                                + "Lệnh dừng do admin tạo không bị hệ thống mở lại.", true),

                // === KẾT NỐI TỦ & ĐỊNH VỊ DRONE ===
                integer(LID_ACK_TIMEOUT_SECONDS, DRONE_LINK, "Chờ tủ xác nhận lệnh nắp",
                        "Tủ chưa trả ACCEPTED cho lệnh mở/đóng nắp sau chừng này giây thì gửi lại đúng lệnh cũ. "
                                + "0 = gửi lại ở lượt quét kế tiếp.", 10, 0, 300, "giây"),
                integer(LID_MOTION_TIMEOUT_SECONDS, DRONE_LINK, "Chờ nắp chạy xong",
                        "Tủ đã nhận lệnh nhưng chưa báo nắp chạy xong sau chừng này giây thì hỏi lại (nắp chạy tối đa "
                                + "~100 giây, nên đặt lớn hơn). 0 = hỏi lại ở lượt quét kế tiếp.", 150, 0, 900, "giây"),
                integer(LID_MAX_RETRIES, DRONE_LINK, "Số lần gửi lại lệnh nắp tối đa",
                        "Hết số lần này mà tủ vẫn không phản hồi thì phiên giao báo lỗi, drone được báo không thả hàng.",
                        5, 0, 50, "lần"),
                integer(LOCKER_OFFLINE_AFTER_SECONDS, DRONE_LINK, "Coi tủ mất kết nối sau",
                        "Không thấy heartbeat của tủ quá chừng này giây thì chờ tủ quay lại thay vì gửi lại lệnh nắp. "
                                + "Pi gửi heartbeat mỗi 60 giây.", 90, 30, 3600, "giây"),
                integer(TELEMETRY_DEPARTED_DISTANCE, DRONE_LINK, "Drone đã rời tủ gửi khi cách",
                        "Telemetry báo drone cách tủ gửi quá mức này thì chuyển chặng sang 'Đang bay' "
                                + "(tuyến ngắn thì lấy ¼ chiều dài tuyến).", 30, 5, 1000, "mét"),
                integer(TELEMETRY_APPROACH_DISTANCE, DRONE_LINK, "Drone đang tiếp cận khi còn cách",
                        "Còn cách tủ nhận dưới mức này thì chuyển chặng sang 'Đang tiếp cận' "
                                + "(tuyến ngắn thì lấy ⅓ chiều dài tuyến).", 150, 10, 5000, "mét"),
                integer(TELEMETRY_ARRIVAL_RADIUS, DRONE_LINK, "Drone đã tới tủ nhận trong bán kính",
                        "Drone đã đáp và cách tủ nhận không quá mức này thì coi là đã tới.", 30, 2, 500, "mét"),

                // === MÔ PHỎNG & THỬ NGHIỆM DRONE (DEMO) ===
                bool(DRONE_DEMO_INDOOR_ARUCO_ENABLED, DRONE_DEMO, "Bật mô phỏng giao hàng bằng nhận diện ArUco trong phòng (Indoor Demo)",
                        "Khi bật (Chế độ B): camera ArUco thật nhận diện đúng mã ô tủ đích thì tủ trượt mở nắp THẬT; "
                                + "việc drone nhả hàng được giả lập theo số giây cấu hình rồi tủ đóng nắp thật, chỉ khi đóng nắp "
                                + "xong mới chuyển Sẵn sàng lấy hàng. Tắt (Chế độ A): quy trình vận hành ngoài trời có GPS, "
                                + "drone tự báo đã nhả hàng.", false),
                bool(DRONE_DEMO_INDOOR_HANDHELD_ENABLED, DRONE_DEMO, "Cho phép demo drone cầm tay (bỏ qua bước KTV)",
                        "Chỉ dùng khi trình diễn trong nhà: khi bật cùng Chế độ B và chỉ có đúng một đơn DEMO đang chờ, "
                                + "camera thấy đúng mã ô là hệ thống tự tạo nhiệm vụ — KHÔNG cần KTV lấy hàng, nạp và phóng. "
                                + "Tắt (mặc định): drone phải được KTV phóng theo đúng quy trình mới được mở nắp tủ.", false),
                integer(DRONE_DEMO_HOLD_OPEN_SECONDS, DRONE_DEMO, "Thời gian giả lập drone nhả hàng (giây)",
                        "Chế độ Indoor Demo: sau khi tủ báo nắp trượt đã mở THẬT, hệ thống giả lập drone nhả hàng "
                                + "trong chừng này giây rồi gửi lệnh đóng nắp thật. Không ảnh hưởng việc mở nắp — nắp luôn "
                                + "được tủ trượt mở thật khi camera nhận đúng mã ArUco.", 7, 1, 300, "giây"),
                integer(DRONE_OPEN_LID_MAX_WAIT_SECONDS, DRONE_DEMO, "Thời gian chờ tủ phản hồi lệnh mở nắp (giây)",
                        "Tủ mất kết nối thì lệnh mở nắp được gửi lại khi tủ kết nối lại. Quá số giây này mà tủ vẫn chưa "
                                + "phản hồi thì huỷ phiên giao (không mở nắp muộn khi drone đã rời đi); drone quét lại mã "
                                + "ArUco sẽ mở phiên mới.", 600, 30, 3600, "giây"),
                bool(DRONE_DEMO_ENABLED, DRONE_DEMO, "Cho phép chế độ drone DEMO",
                        "Bật bộ giả lập chặng bay cho đơn drone.", true),
                integer(DRONE_DEMO_STAGE_DELAY_MS, DRONE_DEMO, "Thời gian mỗi chặng bay giả lập",
                        "Khoảng thời gian (ms) giữa các bước bay trong chế độ giả lập.", 3000, 1000, 600_000, "ms"),
                string(DRONE_DEMO_ALLOWED_USER_IDS, DRONE_DEMO, "User được dùng drone DEMO",
                        "Danh sách user id cách nhau bởi dấu phẩy. Để trống = áp dụng cho mọi người.", "", List.of()));
    }
}

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
    public static final String STORAGE_ITEM_FEE = "app.order.storage-item-fee";
    public static final String OVERTIME_FEE_PER_HOUR = "app.order.pickup-overtime-fee-per-hour";
    public static final String MAX_OVERTIME_FEE = "app.order.pickup-max-overtime-fee";
    public static final String MAX_OVERTIME_PERCENT = "app.order.pickup-max-overtime-percent";
    public static final String SEND_PICKUP_HOURS = "app.order.send-pickup-hours-limit";
    public static final String DRONE_PICKUP_HOURS = "app.order.drone-pickup-hours-limit";
    public static final String AUTO_CANCEL_HOURS = "app.order.auto-cancel-hours";
    public static final String DRONE_UNPAID_CANCEL_MINUTES = "app.order.drone-unpaid-cancel-minutes";
    public static final String DRONE_DISPATCH_TIMEOUT_MINUTES = "app.order.drone-dispatch-timeout-minutes";
    public static final String DRONE_SURCHARGE_TIMEOUT_MINUTES = "app.order.drone-surcharge-timeout-minutes";
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

    private static final String PRICING = "Giá & phí";
    private static final String DEADLINES = "Thời hạn & tự động hoá";
    private static final String RENTAL = "Thuê tủ";
    private static final String PAYMENT = "Thanh toán";
    private static final String DRONE = "Giao hàng drone";
    private static final String RECEIVER = "Thông báo cho người nhận";
    private static final String INCIDENT = "Sự cố & bồi thường";

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
                integer(DRONE_UNPAID_CANCEL_MINUTES, DEADLINES, "Tự huỷ đơn drone chưa thanh toán sau",
                        "Đơn drone chưa trả tiền giữ ô ở cả tủ gửi lẫn tủ nhận; quá thời gian này thì huỷ và nhả ô. "
                                + "0 = không tự huỷ.", 30, 0, 10_080, "phút").asPublic(),
                integer(DRONE_DISPATCH_TIMEOUT_MINUTES, DEADLINES, "Tự huỷ đơn drone đã trả tiền mà chưa được tiếp nhận sau",
                        "Tính từ lúc người gửi bỏ kiện vào ô gửi (chưa bỏ kiện thì từ lúc thanh toán). Đơn bị huỷ và "
                                + "tạo yêu cầu hoàn tiền. 0 = không tự huỷ.", 120, 0, 10_080, "phút").asPublic(),
                integer(DRONE_SURCHARGE_TIMEOUT_MINUTES, DEADLINES, "Tự huỷ đơn drone nợ phụ thu cân lệch sau",
                        "Tính từ lúc nạp hàng. Quá hạn thì huỷ, nhả drone, hoàn phần đã trả và trả kiện cho người gửi. "
                                + "0 = không tự huỷ.", 60, 0, 10_080, "phút").asPublic(),
                integer(AUTO_CANCEL_UNPAID_MINUTES, DEADLINES, "Tự huỷ đơn chưa thanh toán sau",
                        "Đơn INITIALIZED chưa thanh toán sẽ tự động bị huỷ và giải phóng ô tủ sau số phút này nếu khách không hoàn tất thanh toán.",
                        15, 1, 180, "phút").asPublic(),
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
                        "Chặn đặt đơn và xác nhận nạp hàng nếu kiện vượt tải vận hành cho phép.",
                        5000, 1, 100_000, "gram").asPublic(),
                integer(DRONE_BASE_WEIGHT, DRONE, "Khối lượng cơ bản của phí drone",
                        "Kiện tới mức này chỉ trả phí giao hàng drone cơ bản.", 500, 1, 100_000, "gram").asPublic(),
                integer(DRONE_WEIGHT_STEP, DRONE, "Nấc khối lượng tính phụ phí drone",
                        "Mỗi nấc vượt khối lượng cơ bản cộng một lần phụ phí.", 250, 1, 100_000, "gram").asPublic(),
                integerList(DRONE_WEIGHT_OPTIONS, DRONE, "Các mức khối lượng khách chọn khi đặt drone",
                        "Danh sách gram hiển thị trên app, phân tách bằng dấu phẩy. Mức vượt tải tối đa bị ẩn.",
                        "500,750,1000,1500,2000,3000", 1, 100_000, "gram").asPublic(),
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
                integer(DRONE_MAX_OPEN_ORDERS, DRONE, "Số đơn drone đang mở tối đa mỗi khách",
                        "Mỗi đơn drone chưa giao xong giữ ô ở hai tủ. 0 = không giới hạn.", 3, 0, 100, "đơn").asPublic(),
                integer(DRONE_MAX_PARCEL_LENGTH, DRONE, "Chiều dài kiện tối đa", "Theo khoang hàng của drone.",
                        30, 1, 500, "cm").asPublic(),
                integer(DRONE_MAX_PARCEL_WIDTH, DRONE, "Chiều rộng kiện tối đa", "", 25, 1, 500, "cm").asPublic(),
                integer(DRONE_MAX_PARCEL_HEIGHT, DRONE, "Chiều cao kiện tối đa", "", 20, 1, 500, "cm").asPublic(),
                integer(DRONE_MAX_DECLARED_VALUE, DRONE, "Giá trị khai báo tối đa của kiện drone",
                        "Kiện có giá trị khai báo cao hơn không được nhận.", 2_000_000, 0, 1_000_000_000, "VND")
                        .asPublic());
    }
}

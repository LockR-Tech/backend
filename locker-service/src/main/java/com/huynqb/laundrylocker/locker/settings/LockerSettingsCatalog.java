package com.huynqb.laundrylocker.locker.settings;

import com.huynqb.laundrylocker.common.settings.SettingDefinition;
import com.huynqb.laundrylocker.common.settings.SettingsCatalog;
import org.springframework.stereotype.Component;

import java.util.List;

import static com.huynqb.laundrylocker.common.settings.SettingDefinition.bool;
import static com.huynqb.laundrylocker.common.settings.SettingDefinition.integer;
import static com.huynqb.laundrylocker.common.settings.SettingDefinition.integerList;
import static com.huynqb.laundrylocker.common.settings.SettingDefinition.string;

/// Quy tắc nghiệp vụ của locker-service admin chỉnh được (ADR-0005).
/// Key giữ nguyên tên property cũ ⇒ biến môi trường đang đặt trên VM vẫn là mặc định.
@Component
public class LockerSettingsCatalog implements SettingsCatalog {

    public static final String SLA_HOURS = "app.maintenance.sla-hours";
    public static final String SLA_MAX_EXTENSIONS = "app.maintenance.sla-max-extensions";
    public static final String SLA_MAX_HOURS_PER_EXTENSION = "app.maintenance.sla-max-hours-per-extension";
    public static final String TECHNICIAN_CLAIM_BLOCK_OVERDUE = "app.maintenance.technician-claim-block-overdue";
    public static final String PENALTY_WARNING_OVERDUE = "app.maintenance.penalty-warning-overdue";
    public static final String PENALTY_RESTRICTED_OVERDUE = "app.maintenance.penalty-restricted-overdue";
    public static final String PENALTY_SUSPENDED_OVERDUE = "app.maintenance.penalty-suspended-overdue";
    public static final String REQUIRE_RESOLUTION_PHOTO = "app.maintenance.require-resolution-photo";
    public static final String SCHEDULE_REMINDER_LEAD_HOURS = "app.maintenance.schedule-reminder-lead-hours";
    public static final String REPORT_PHOTOS_PER_REQUEST_REPORTER = "app.maintenance.report-photos-per-request-reporter";
    public static final String REPORT_PHOTOS_PER_REQUEST_STAFF = "app.maintenance.report-photos-per-request-staff";
    public static final String REPORT_PHOTOS_REPORTER_TOTAL = "app.maintenance.report-photos-reporter-total";
    public static final String REPORT_PHOTOS_TOTAL = "app.maintenance.report-photos-total";
    public static final String PENDING_CHECK_HOLD_HOURS = "app.maintenance.pending-check-hold-hours";
    public static final String RESERVED_TTL_HOURS = "app.locker.reserved-ttl-hours";
    public static final String CELL_DIMENSIONS_STANDARD = "app.locker.cell-dimensions-standard";
    public static final String CELL_DIMENSIONS_XL = "app.locker.cell-dimensions-xl";
    public static final String DRONE_LOW_BATTERY_PERCENT = "app.locker.drone-low-battery-percent";
    public static final String TECHNICIAN_LOCATION_MAX_AGE_MINUTES = "app.locker.technician-location-max-age-minutes";
    public static final String DEMO_VISIBLE = "app.locker.demo.visible";
    public static final String DEMO_LOCKER_IDS = "app.locker.demo.locker-ids";
    public static final String DEMO_ALLOWED_USER_IDS = "app.locker.demo.allowed-user-ids";

    private static final String MAINTENANCE = "Bảo trì & SLA";
    private static final String CELLS = "Ô tủ";
    private static final String DRONE = "Drone";
    private static final String DEMO = "Giả lập & demo";

    /// Tủ demo của bộ seed `private/seed-demo` (HCM-…, id 800001–800013): bộ giả lập trên VM trả lời thay tủ thật.
    static final String SEED_DEMO_LOCKER_IDS =
            "800001,800002,800003,800004,800005,800006,800007,800008,800009,800010,800011,800012,800013";
    /// Tài khoản demo đăng nhập được (`*@demo.lockr.test`): 4 KTV tủ, 4 KTV drone, khách demo.
    static final String SEED_DEMO_USER_IDS = "800011,800012,800013,800014,800021,800022,800023,800024,800101";

    @Override
    public List<SettingDefinition> definitions() {
        return List.of(
                integer(SLA_HOURS, MAINTENANCE, "SLA xử lý phiếu bảo trì",
                        "Số giờ tối đa từ lúc tạo phiếu đến khi xử lý xong; quá mốc này phiếu bị tính trễ hạn.",
                        4, 1, 720, "giờ"),
                integer(SLA_MAX_EXTENSIONS, MAINTENANCE, "Số lần KTV được tự gia hạn SLA mỗi phiếu",
                        "KTV đang giữ phiếu tự gia hạn hạn xử lý tối đa số lần này (phải nêu lý do). "
                                + "0 = KTV không được tự gia hạn; admin gia hạn trên web không tính vào giới hạn.",
                        2, 0, 20, "lần").asPublic(),
                integer(SLA_MAX_HOURS_PER_EXTENSION, MAINTENANCE, "Số giờ tối đa mỗi lần KTV gia hạn SLA",
                        "Mỗi lần KTV tự gia hạn chỉ được cộng thêm tối đa số giờ này vào hạn xử lý của phiếu.",
                        24, 1, 720, "giờ").asPublic(),
                integer(TECHNICIAN_CLAIM_BLOCK_OVERDUE, MAINTENANCE, "Chặn KTV nhận việc khi trễ hạn từ",
                        "KTV đang giữ từ số phiếu trễ hạn SLA này trở lên (đang xử lý) thì không nhận thêm phiếu mới.",
                        3, 1, 100, "phiếu"),
                integer(PENALTY_WARNING_OVERDUE, MAINTENANCE, "Mức cảnh báo khi trễ hạn từ",
                        "Số phiếu trễ hạn để xếp KTV vào mức cảnh báo. Nếu đặt lớn hơn mức hạn chế/đình chỉ, "
                                + "hệ thống tự nâng các mức sau cho bằng (cảnh báo ≤ hạn chế ≤ đình chỉ).",
                        1, 1, 100, "phiếu"),
                integer(PENALTY_RESTRICTED_OVERDUE, MAINTENANCE, "Mức hạn chế khi trễ hạn từ",
                        "Số phiếu trễ hạn để xếp KTV vào mức hạn chế. Nhỏ hơn mức cảnh báo thì dùng mức cảnh báo.",
                        3, 1, 100, "phiếu"),
                integer(PENALTY_SUSPENDED_OVERDUE, MAINTENANCE, "Mức đình chỉ khi trễ hạn từ",
                        "Số phiếu trễ hạn để xếp KTV vào mức đình chỉ. Nhỏ hơn mức hạn chế thì dùng mức hạn chế.",
                        5, 1, 100, "phiếu"),
                bool(REQUIRE_RESOLUTION_PHOTO, MAINTENANCE, "Bắt buộc ảnh nghiệm thu",
                        "KTV phải có ít nhất một ảnh nghiệm thu mới hoàn tất được phiếu (admin không bị chặn).",
                        false).asPublic(),
                integer(SCHEDULE_REMINDER_LEAD_HOURS, MAINTENANCE, "Nhắc lịch kiểm tra trước hạn",
                        "Mỗi sáng (07:00) nhắc KTV các lịch định kỳ sẽ tới hạn trong số giờ này hoặc đã quá hạn; "
                                + "mỗi kỳ chỉ nhắc một lần. 0 = chỉ nhắc khi đã tới hạn.",
                        24, 0, 720, "giờ"),
                integer(REPORT_PHOTOS_PER_REQUEST_REPORTER, MAINTENANCE, "Số ảnh người báo gửi mỗi lần",
                        "Số ảnh hiện trường tối đa trong một lần báo sự cố hoặc bổ sung ảnh.", 5, 1, 50, "ảnh")
                        .asPublic(),
                integer(REPORT_PHOTOS_PER_REQUEST_STAFF, MAINTENANCE, "Số ảnh KTV/admin gửi mỗi lần",
                        "Số ảnh xác nhận/quá trình/nghiệm thu tối đa trong một lần gửi.", 10, 1, 50, "ảnh")
                        .asPublic(),
                integer(REPORT_PHOTOS_REPORTER_TOTAL, MAINTENANCE, "Tổng ảnh hiện trường mỗi phiếu",
                        "Tổng số ảnh giai đoạn báo sự cố tối đa trên một phiếu.", 10, 1, 200, "ảnh"),
                integer(REPORT_PHOTOS_TOTAL, MAINTENANCE, "Tổng ảnh mỗi phiếu",
                        "Tổng số ảnh mọi giai đoạn tối đa trên một phiếu.", 30, 1, 500, "ảnh"),
                integer(PENDING_CHECK_HOLD_HOURS, MAINTENANCE, "Giữ ô chờ KTV kiểm tra tối đa",
                        "Khách báo sự cố ⇒ ô tạm ngưng cấp cho đơn mới, chờ KTV kiểm tra (chủ đồ vẫn mở được). Quá số "
                                + "giờ này chưa có KTV kiểm tra thì ô tự trở lại hoạt động, phiếu vẫn mở. 0 = giữ tới khi KTV xử lý.",
                        24, 0, 720, "giờ"),

                integer(RESERVED_TTL_HOURS, CELLS, "Thời gian giữ chỗ ô",
                        "Ô đang giữ chỗ quá thời gian này được nhả về trống. Nên đặt ≥ quy tắc «Tự huỷ đơn chưa bỏ hàng sau».",
                        24, 1, 720, "giờ"),
                string(CELL_DIMENSIONS_STANDARD, CELLS, "Kích thước ô tiêu chuẩn",
                        "Chỉ để hiển thị trên app (dài × rộng × cao).", "45 × 30 × 50 cm", List.of()).asPublic(),
                string(CELL_DIMENSIONS_XL, CELLS, "Kích thước ô XL",
                        "Chỉ để hiển thị trên app (dài × rộng × cao).", "30 × 80 × 40 cm", List.of()).asPublic(),

                integer(DRONE_LOW_BATTERY_PERCENT, DRONE, "Pin tối thiểu để drone cất cánh",
                        "Drone có pin ≤ ngưỡng này không được chuyển sang trạng thái đang bay.", 20, 0, 100, "%").asPublic(),
                integer(TECHNICIAN_LOCATION_MAX_AGE_MINUTES, MAINTENANCE, "Vị trí KTV còn hiệu lực khi phân công kiện rơi",
                        "Khi drone báo rơi kiện, hệ thống giao việc cho KTV trong ca ở gần nhất; vị trí KTV cũ hơn mức này "
                                + "thì không được xét.", 60, 1, 1440, "phút"),
                // Cụm "Giả lập & demo" trên web admin tắt/bật cờ này cùng các cờ giả lập khác (F3-G14).
                bool(DEMO_VISIBLE, DEMO, "Hiện tủ demo cho khách",
                        "Tắt khi vận hành chính thức: tủ trong danh sách tủ demo biến khỏi app, kiosk và không nhận "
                                + "đơn mới của khách; admin và tài khoản trong danh sách được phép vẫn thấy để demo.", true),
                integerList(DEMO_LOCKER_IDS, DEMO, "Danh sách tủ demo",
                        "Id tủ demo (giả lập, không có tủ thật), cách nhau bởi dấu phẩy. 0 = không có.",
                        SEED_DEMO_LOCKER_IDS, 0, null, null),
                integerList(DEMO_ALLOWED_USER_IDS, DEMO, "Tài khoản vẫn thấy tủ demo khi đã ẩn",
                        "Id người dùng vẫn thấy và đặt được tủ demo khi đã ẩn (mặc định: các tài khoản *@demo.lockr.test). "
                                + "0 = chỉ admin.", SEED_DEMO_USER_IDS, 0, null, null));
    }
}

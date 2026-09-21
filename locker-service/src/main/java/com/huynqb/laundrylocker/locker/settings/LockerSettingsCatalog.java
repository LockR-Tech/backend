package com.huynqb.laundrylocker.locker.settings;

import com.huynqb.laundrylocker.common.settings.SettingDefinition;
import com.huynqb.laundrylocker.common.settings.SettingsCatalog;
import org.springframework.stereotype.Component;

import java.util.List;

import static com.huynqb.laundrylocker.common.settings.SettingDefinition.bool;
import static com.huynqb.laundrylocker.common.settings.SettingDefinition.integer;
import static com.huynqb.laundrylocker.common.settings.SettingDefinition.string;

/// Quy tắc nghiệp vụ của locker-service admin chỉnh được (ADR-0005).
/// Key giữ nguyên tên property cũ ⇒ biến môi trường đang đặt trên VM vẫn là mặc định.
@Component
public class LockerSettingsCatalog implements SettingsCatalog {

    public static final String SLA_HOURS = "app.maintenance.sla-hours";
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
    public static final String RESERVED_TTL_HOURS = "app.locker.reserved-ttl-hours";
    public static final String CELL_DIMENSIONS_STANDARD = "app.locker.cell-dimensions-standard";
    public static final String CELL_DIMENSIONS_XL = "app.locker.cell-dimensions-xl";
    public static final String DRONE_LOW_BATTERY_PERCENT = "app.locker.drone-low-battery-percent";

    private static final String MAINTENANCE = "Bảo trì & SLA";
    private static final String CELLS = "Ô tủ";
    private static final String DRONE = "Drone";

    @Override
    public List<SettingDefinition> definitions() {
        return List.of(
                integer(SLA_HOURS, MAINTENANCE, "SLA xử lý phiếu bảo trì",
                        "Số giờ tối đa từ lúc tạo phiếu đến khi xử lý xong; quá mốc này phiếu bị tính trễ hạn.",
                        4, 1, 720, "giờ"),
                integer(TECHNICIAN_CLAIM_BLOCK_OVERDUE, MAINTENANCE, "Chặn KTV nhận việc khi trễ hạn từ",
                        "KTV đang giữ từ số phiếu trễ hạn SLA này trở lên (đang xử lý) thì không nhận thêm phiếu mới.",
                        3, 1, 100, "phiếu"),
                integer(PENALTY_WARNING_OVERDUE, MAINTENANCE, "Mức CẢNH BÁO khi trễ hạn từ",
                        "Số phiếu trễ hạn để xếp KTV vào mức WARNING. Nếu đặt lớn hơn mức HẠN CHẾ/ĐÌNH CHỈ, "
                                + "hệ thống tự nâng các mức sau cho bằng (cảnh báo ≤ hạn chế ≤ đình chỉ).",
                        1, 1, 100, "phiếu"),
                integer(PENALTY_RESTRICTED_OVERDUE, MAINTENANCE, "Mức HẠN CHẾ khi trễ hạn từ",
                        "Số phiếu trễ hạn để xếp KTV vào mức RESTRICTED. Nhỏ hơn mức cảnh báo thì dùng mức cảnh báo.",
                        3, 1, 100, "phiếu"),
                integer(PENALTY_SUSPENDED_OVERDUE, MAINTENANCE, "Mức ĐÌNH CHỈ khi trễ hạn từ",
                        "Số phiếu trễ hạn để xếp KTV vào mức SUSPENDED. Nhỏ hơn mức hạn chế thì dùng mức hạn chế.",
                        5, 1, 100, "phiếu"),
                bool(REQUIRE_RESOLUTION_PHOTO, MAINTENANCE, "Bắt buộc ảnh nghiệm thu",
                        "KTV phải có ít nhất một ảnh nghiệm thu mới hoàn tất được phiếu (admin không bị chặn).",
                        false),
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
                        "Tổng số ảnh giai đoạn báo sự cố (REPORT) tối đa trên một phiếu.", 10, 1, 200, "ảnh"),
                integer(REPORT_PHOTOS_TOTAL, MAINTENANCE, "Tổng ảnh mỗi phiếu",
                        "Tổng số ảnh mọi giai đoạn tối đa trên một phiếu.", 30, 1, 500, "ảnh"),

                integer(RESERVED_TTL_HOURS, CELLS, "Thời gian giữ ô RESERVED",
                        "Ô giữ chỗ quá thời gian này sẽ được nhả về AVAILABLE. Nên ≥ thời gian tự huỷ đơn bên order.",
                        24, 1, 720, "giờ"),
                string(CELL_DIMENSIONS_STANDARD, CELLS, "Kích thước ô tiêu chuẩn",
                        "Chỉ để hiển thị trên app (dài × rộng × cao).", "45 × 30 × 50 cm", List.of()).asPublic(),
                string(CELL_DIMENSIONS_XL, CELLS, "Kích thước ô XL",
                        "Chỉ để hiển thị trên app (dài × rộng × cao).", "30 × 80 × 40 cm", List.of()).asPublic(),

                integer(DRONE_LOW_BATTERY_PERCENT, DRONE, "Pin tối thiểu để drone cất cánh",
                        "Drone có pin ≤ ngưỡng này không được chuyển sang IN_FLIGHT.", 20, 0, 100, "%"));
    }
}

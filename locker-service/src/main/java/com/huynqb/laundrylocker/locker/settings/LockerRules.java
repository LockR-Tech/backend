package com.huynqb.laundrylocker.locker.settings;

import com.huynqb.laundrylocker.common.settings.BusinessSettings;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import static com.huynqb.laundrylocker.locker.settings.LockerSettingsCatalog.*;

/// Đọc có kiểu các quy tắc nghiệp vụ của locker-service. Mỗi lần gọi lấy giá trị đang áp dụng
/// (admin sửa có hiệu lực tối đa sau 30 giây, ngay lập tức trên instance nhận lệnh sửa).
@Component
@RequiredArgsConstructor
public class LockerRules {

    private final BusinessSettings settings;

    public int slaHours() {
        return settings.getInt(SLA_HOURS);
    }

    public int technicianClaimBlockOverdue() {
        return settings.getInt(TECHNICIAN_CLAIM_BLOCK_OVERDUE);
    }

    /// Ba mức chế tài luôn tăng dần: cảnh báo ≤ hạn chế ≤ đình chỉ (mức sau nhỏ hơn thì nâng lên bằng mức trước).
    public PenaltyThresholds penaltyThresholds() {
        int warning = settings.getInt(PENALTY_WARNING_OVERDUE);
        int restricted = Math.max(warning, settings.getInt(PENALTY_RESTRICTED_OVERDUE));
        int suspended = Math.max(restricted, settings.getInt(PENALTY_SUSPENDED_OVERDUE));
        return new PenaltyThresholds(warning, restricted, suspended);
    }

    public boolean requireResolutionPhoto() {
        return settings.getBoolean(REQUIRE_RESOLUTION_PHOTO);
    }

    public int scheduleReminderLeadHours() {
        return settings.getInt(SCHEDULE_REMINDER_LEAD_HOURS);
    }

    public int reportPhotosPerRequestReporter() {
        return settings.getInt(REPORT_PHOTOS_PER_REQUEST_REPORTER);
    }

    public int reportPhotosPerRequestStaff() {
        return settings.getInt(REPORT_PHOTOS_PER_REQUEST_STAFF);
    }

    public int reportPhotosReporterTotal() {
        return settings.getInt(REPORT_PHOTOS_REPORTER_TOTAL);
    }

    public int reportPhotosTotal() {
        return settings.getInt(REPORT_PHOTOS_TOTAL);
    }

    public int reservedTtlHours() {
        return settings.getInt(RESERVED_TTL_HOURS);
    }

    public int droneLowBatteryPercent() {
        return settings.getInt(DRONE_LOW_BATTERY_PERCENT);
    }

    public String cellDimensionsStandard() {
        return settings.getString(CELL_DIMENSIONS_STANDARD);
    }

    public String cellDimensionsXl() {
        return settings.getString(CELL_DIMENSIONS_XL);
    }

    /// Số phiếu trễ hạn tối thiểu cho từng mức chế tài KTV.
    public record PenaltyThresholds(int warning, int restricted, int suspended) {
    }
}

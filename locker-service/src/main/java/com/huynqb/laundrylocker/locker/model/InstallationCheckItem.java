package com.huynqb.laundrylocker.locker.model;

/// Các bước nghiệm thu lắp đặt tủ. `auto` = hệ thống tự xác nhận từ dữ liệu của tủ (KTV không tích tay);
/// `photoRequired` = phải có ít nhất 1 ảnh gắn với bước; `notApplicableAllowed` = KTV được đánh dấu "không áp dụng".
public enum InstallationCheckItem {
    MOUNTING("Lắp đặt cố định & cấp điện", false, true, false),
    NETWORK("Kết nối mạng (bộ điều khiển online)", true, false, false),
    CONTROLLER("Gán bộ điều khiển & cài sơ đồ ô", true, false, false),
    HARDWARE_CONFIG("Áp cấu hình phần cứng", true, false, false),
    BOX_TEST("Mở thử từng ô", true, false, false),
    LID("Thử nắp trượt / bãi đáp", true, false, true),
    KIOSK("Màn hình kiosk hoạt động", false, true, false),
    BLUETOOTH("Mở ô bằng Bluetooth", false, false, true);

    private final String label;
    private final boolean auto;
    private final boolean photoRequired;
    private final boolean notApplicableAllowed;

    InstallationCheckItem(String label, boolean auto, boolean photoRequired, boolean notApplicableAllowed) {
        this.label = label;
        this.auto = auto;
        this.photoRequired = photoRequired;
        this.notApplicableAllowed = notApplicableAllowed;
    }

    public String label() {
        return label;
    }

    public boolean auto() {
        return auto;
    }

    public boolean photoRequired() {
        return photoRequired;
    }

    public boolean notApplicableAllowed() {
        return notApplicableAllowed;
    }
}

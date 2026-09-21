package com.huynqb.laundrylocker.locker.model;

/// Phiếu sự cố gắn với tài sản nào (locker_reports.category) — quyết định định tuyến
/// và cái gì được khôi phục khi phiếu đóng.
public final class ReportCategory {

    private ReportCategory() {
    }

    /// Một ô tủ (box_id có giá trị).
    public static final String BOX = "BOX";

    /// Một drone vật lý — việc của KTV drone, không định tuyến cho KTV tủ.
    public static final String DRONE = "DRONE";

    /// Bãi đáp drone trên nóc tủ.
    public static final String LANDING_PAD = "LANDING_PAD";

    /// Cả tủ (nguồn điện, màn hình, mạng…).
    public static final String LOCKER = "LOCKER";
}

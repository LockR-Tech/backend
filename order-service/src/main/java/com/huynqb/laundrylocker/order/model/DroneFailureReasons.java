package com.huynqb.laundrylocker.order.model;

import java.util.Map;
import java.util.Set;

/// Vì sao một đơn drone đã thanh toán không phục vụ được: dùng cho cả lúc đơn đang bị chặn
/// (`orders.delivery_block_reason`) lẫn lúc đơn kết thúc "giao không hoàn tất"
/// (`orders.failure_reason`). Một bảng mã cho khách, điều phối, KTV sự cố và admin cùng đọc.
public final class DroneFailureReasons {

    private DroneFailureReasons() {
    }

    /// Admin bật "Tạm dừng bay" (thời tiết xấu, lệnh cấm bay…).
    public static final String WEATHER = "WEATHER";
    /// Dự báo trên tuyến bay của đơn vượt ngưỡng an toàn (mưa, gió, tầm nhìn) — không cần admin bấm gì.
    public static final String WEATHER_UNSAFE = "WEATHER_UNSAFE";
    /// Admin tạm dừng bay ở trạm cất cánh hoặc tủ nhận của đơn.
    public static final String STATION_SUSPENDED = "STATION_SUSPENDED";
    /// Drone của nhiệm vụ lỗi/bảo trì/ngừng hoạt động.
    public static final String DRONE_FAULT = "DRONE_FAULT";
    /// Tủ gửi không còn drone nào dùng được.
    public static final String NO_DRONE_AVAILABLE = "NO_DRONE_AVAILABLE";
    /// Tủ gửi ngừng hoạt động/bảo trì.
    public static final String SOURCE_LOCKER_FAULT = "SOURCE_LOCKER_FAULT";
    /// Tủ nhận ngừng hoạt động/bảo trì, hoặc ô nhận đã giữ bị hỏng.
    public static final String DESTINATION_LOCKER_FAULT = "DESTINATION_LOCKER_FAULT";
    /// Bãi đáp ở tủ gửi hoặc tủ nhận không sẵn sàng.
    public static final String LANDING_PAD_FAULT = "LANDING_PAD_FAULT";
    /// Không có KTV drone nào nhận lấy hàng trong hạn.
    public static final String NO_TECHNICIAN_AVAILABLE = "NO_TECHNICIAN_AVAILABLE";
    /// Đã lấy hàng nhưng quá hạn mà drone chưa cất cánh.
    public static final String LAUNCH_OVERDUE = "LAUNCH_OVERDUE";
    /// KTV tới nơi nhưng không gặp được người gửi.
    public static final String PICKUP_CUSTOMER_ABSENT = "PICKUP_CUSTOMER_ABSENT";
    /// Kiện không đúng khai báo: quá nặng, quá khổ, hàng cấm bay…
    public static final String PICKUP_PARCEL_REJECTED = "PICKUP_PARCEL_REJECTED";
    /// Không tìm thấy địa chỉ lấy hàng.
    public static final String PICKUP_ADDRESS_NOT_FOUND = "PICKUP_ADDRESS_NOT_FOUND";
    /// Sự cố trong lúc bay (mất tín hiệu, hạ cánh khẩn…).
    public static final String FLIGHT_INCIDENT = "FLIGHT_INCIDENT";
    public static final String OPERATIONAL = "OPERATIONAL";
    public static final String OTHER = "OTHER";

    private static final Map<String, String> LABELS = Map.ofEntries(
            Map.entry(WEATHER, "Tạm dừng bay do thời tiết hoặc lệnh điều hành"),
            Map.entry(WEATHER_UNSAFE, "Thời tiết tuyến bay không an toàn"),
            Map.entry(STATION_SUSPENDED, "Trạm bay đang tạm dừng"),
            Map.entry(DRONE_FAULT, "Drone gặp sự cố"),
            Map.entry(NO_DRONE_AVAILABLE, "Tủ gửi không còn drone sẵn sàng"),
            Map.entry(SOURCE_LOCKER_FAULT, "Tủ gửi gặp sự cố"),
            Map.entry(DESTINATION_LOCKER_FAULT, "Tủ nhận gặp sự cố"),
            Map.entry(LANDING_PAD_FAULT, "Bãi đáp drone không sẵn sàng"),
            Map.entry(NO_TECHNICIAN_AVAILABLE, "Không có kỹ thuật viên nhận lấy hàng"),
            Map.entry(LAUNCH_OVERDUE, "Quá hạn phóng drone sau khi lấy hàng"),
            Map.entry(PICKUP_CUSTOMER_ABSENT, "Không gặp được người gửi tại điểm lấy hàng"),
            Map.entry(PICKUP_PARCEL_REJECTED, "Kiện hàng không đúng khai báo"),
            Map.entry(PICKUP_ADDRESS_NOT_FOUND, "Không tìm thấy địa chỉ lấy hàng"),
            Map.entry(FLIGHT_INCIDENT, "Sự cố trong lúc bay"),
            Map.entry(OPERATIONAL, "Lý do vận hành"),
            Map.entry(OTHER, "Lý do khác"));

    /// Lý do KTV được chọn khi tới nơi mà không lấy được hàng.
    public static final Set<String> PICKUP_FAILURES =
            Set.of(PICKUP_CUSTOMER_ABSENT, PICKUP_PARCEL_REJECTED, PICKUP_ADDRESS_NOT_FOUND, OTHER);

    /// Lý do điều phối viên/admin được chọn khi xác nhận đơn giao không hoàn tất.
    public static final Set<String> MANUAL = Set.of(
            WEATHER, DRONE_FAULT, NO_DRONE_AVAILABLE, SOURCE_LOCKER_FAULT, DESTINATION_LOCKER_FAULT,
            LANDING_PAD_FAULT, FLIGHT_INCIDENT, OPERATIONAL, OTHER);

    public static boolean known(String code) {
        return code != null && LABELS.containsKey(code);
    }

    public static String label(String code) {
        return code == null ? null : LABELS.getOrDefault(code, code);
    }

    /// Mã số cũ của `orders.cancel_reason` (1 thời tiết · 2 drone lỗi · 3 bãi đáp · 4 vận hành ·
    /// 5 khác) — client và báo cáo cũ vẫn đọc cột này.
    public static int legacyCancelCode(String code) {
        return switch (code == null ? "" : code) {
            case WEATHER, WEATHER_UNSAFE -> 1;
            case DRONE_FAULT, NO_DRONE_AVAILABLE, FLIGHT_INCIDENT -> 2;
            case LANDING_PAD_FAULT -> 3;
            case OTHER -> 5;
            default -> 4;
        };
    }

    /// Nhãn hiển thị cho mã số lý do KTV chọn khi huỷ trước khi bay (1 thời tiết · 2 drone · 3 bãi đáp ·
    /// 4 vận hành · 5 khác) — đúng chữ trên nút của app, không phải nhãn sự cố hệ thống.
    public static String cancelLabel(Integer code) {
        return switch (code == null ? 0 : code) {
            case 1 -> "Thời tiết xấu";
            case 2 -> "Drone lỗi";
            case 3 -> "Bãi đáp không sẵn sàng";
            case 4 -> "Lý do vận hành";
            case 5 -> "Khác";
            default -> "Không rõ lý do";
        };
    }

    /// Chiều ngược lại, cho các lệnh cũ còn gửi mã số.
    public static String fromLegacyCancelCode(Integer code) {
        return switch (code == null ? 0 : code) {
            case 1 -> WEATHER;
            case 2 -> DRONE_FAULT;
            case 3 -> LANDING_PAD_FAULT;
            case 4 -> OPERATIONAL;
            default -> OTHER;
        };
    }
}

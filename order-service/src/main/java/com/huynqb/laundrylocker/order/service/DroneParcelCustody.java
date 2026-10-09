package com.huynqb.laundrylocker.order.service;

import com.huynqb.laundrylocker.order.model.DroneMission;
import com.huynqb.laundrylocker.order.model.LockerOrder;

/// Kiện của một đơn drone đang nằm ở đâu khi đơn kết thúc mà không giao được. Một chỗ duy
/// nhất để lúc huỷ, read model và bước xác nhận trả kiện không lệch nhau.
public final class DroneParcelCustody {

    /// Kiện còn trong ô DRONE ở tủ gửi (người gửi đã bỏ vào, đội bay chưa nạp lên drone).
    public static final String SOURCE_BOX = "SOURCE_BOX";
    /// Kiện đã được nạp lên drone — đội bay đang giữ.
    public static final String FLIGHT_TEAM = "FLIGHT_TEAM";

    private DroneParcelCustody() {
    }

    /// Kiện đã vào hệ thống: người gửi xác nhận bỏ kiện, hoặc (đơn cũ) đội bay đã nạp hàng.
    public static boolean inSystem(LockerOrder order, DroneMission mission) {
        return order.getParcelDroppedAt() != null || loaded(mission);
    }

    /// Đơn đã đóng mà không giao được và kiện chưa được trả lại cho người gửi.
    public static boolean returnPending(LockerOrder order, DroneMission mission) {
        return "CANCELED".equals(order.getStatus())
                && order.getParcelReturnedAt() == null
                && inSystem(order, mission);
    }

    /// Kiện còn nằm trong ô gửi ⇒ ô đó phải được giữ tới khi trả kiện.
    public static boolean inSourceBox(LockerOrder order, DroneMission mission) {
        return order.getParcelDroppedAt() != null && !loaded(mission) && order.getSourceBoxId() != null;
    }

    public static String heldAt(LockerOrder order, DroneMission mission) {
        return loaded(mission) ? FLIGHT_TEAM : SOURCE_BOX;
    }

    private static boolean loaded(DroneMission mission) {
        return mission != null && mission.getLoadedAt() != null;
    }
}

package com.huynqb.laundrylocker.locker.model;

import java.util.Set;

/**
 * Trạng thái của một con drone vật lý (drone_units.status), khác với
 * {@link CellType#DRONE} là loại ô tủ nhận hàng từ drone thả xuống.
 */
public final class DroneStatus {

    private DroneStatus() {
    }

    /**
     * Sẵn sàng nhận nhiệm vụ mới.
     */
    public static final String IDLE = "IDLE";

    /**
     * Da duoc giu rieng cho mot mission, chua duoc phep cat canh.
     */
    public static final String RESERVED = "RESERVED";

    /**
     * Đang sạc pin.
     */
    public static final String CHARGING = "CHARGING";

    /**
     * Đang thực hiện chuyến bay/giao hàng.
     */
    public static final String IN_FLIGHT = "IN_FLIGHT";

    /**
     * Đang được kỹ thuật viên bảo trì định kỳ.
     */
    public static final String MAINTENANCE = "MAINTENANCE";

    /**
     * Gặp lỗi, cần kỹ thuật viên xử lý trước khi dùng lại.
     */
    public static final String FAULT = "FAULT";

    public static final Set<String> ALL = Set.of(IDLE, RESERVED, CHARGING, IN_FLIGHT, MAINTENANCE, FAULT);
}

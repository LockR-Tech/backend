package com.huynqb.laundrylocker.locker.model;

/**
 * Các loại ô tủ (cell_type) được lưu dưới dạng chuỗi VARCHAR(30) trong bảng locker_boxes.
 *
 * <p>Mỗi loại quyết định:
 * <ul>
 *   <li>Dịch vụ nào có thể dùng ô đó (drone drop / đa dịch vụ / vali).</li>
 *   <li>Cách hiển thị trên màn hình tủ cảm ứng và ứng dụng mobile (icon, màu sắc).</li>
 *   <li>Logic phân bổ ô khi tạo đơn hàng.</li>
 * </ul>
 *
 * <h3>Bố cục vật lý của tủ thực tế (CAB-PROD)</h3>
 * <pre>
 *  col →  0 (Màn hình)   1 (Phải)    2 (Phải)
 * row 0                  [D]         [D]    ← DRONE: nhận hàng từ drone thả xuống
 * row 1  [XL]            [S]         [S]    ← STANDARD ở cột 1-2
 * row 2                  [S]         [S]    ← STANDARD đa dịch vụ
 * </pre>
 *
 * <p>Các hằng số này được dùng để tránh "magic string" trong service và validator.
 * Trường {@code cellType} trong {@link LockerBox} và {@link com.huynqb.laundrylocker.locker.dto.CellResponse}
 * luôn nhận một trong ba giá trị dưới đây.
 */
public final class CellType {

    private CellType() {
    }

    /**
     * Ô đa dịch vụ — mặc định cho toàn bộ hàng 2 trở xuống.
     *
     * <p>Chấp nhận: thuê tủ giữ đồ, gửi/nhận hàng C2C, giặt ủi.
     * Kích thước: 45 × 30 × 50 cm.
     */
    public static final String STANDARD = "STANDARD";

    /**
     * Ô nhận hàng từ drone — chỉ ở hàng 1 (row_index = 1), ngay dưới bãi đáp ArUco trên nóc tủ.
     *
     * <p>Không chấp nhận đặt dịch vụ thông thường qua app. Hàng chỉ được bỏ vào bởi drone
     * và lấy ra bởi người nhận qua PIN/QR. Mobile app hiển thị ô này với icon drone;
     * người dùng không thể tap để đặt dịch vụ.
     * Kích thước: 45 × 30 × 50 cm (giống STANDARD, nhưng mở nóc).
     */
    public static final String DRONE = "DRONE";

    /**
     * Ô vali cỡ lớn — khu bên trái (col_index = 0), chiếm chiều cao 2 hàng.
     *
     * <p>Chấp nhận: thuê tủ giữ đồ (loại XL). Không dùng cho gửi hàng C2C hay giặt ủi.
     * Kích thước: 30 × 80 × 40 cm.
     * Hiển thị trên mobile với label "Ô vali (XL)", rate 10.000đ/giờ.
     */
    public static final String XL = "XL";
}

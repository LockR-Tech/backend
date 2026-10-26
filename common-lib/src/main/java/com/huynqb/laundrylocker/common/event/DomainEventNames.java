package com.huynqb.laundrylocker.common.event;

public final class DomainEventNames {

    public static final String EXCHANGE = "laundry.events";
    public static final String ORDER_CREATED = "order.created";
    public static final String ORDER_STATUS_CHANGED = "order.status.changed";
    /// Đơn vừa chuyển sang COMPLETED (một lần mỗi đơn). Payload: orderId, orderCode, userId, type,
    /// paidAmount, totalPrice, paymentStatus, completedAt — loyalty-service cộng điểm từ sự kiện này.
    public static final String ORDER_COMPLETED = "order.completed";
    public static final String PAYMENT_COMPLETED = "payment.completed";
    public static final String PAYMENT_FAILED = "payment.failed";
    /// Admin vừa xác nhận đã chuyển khoản hoàn tiền cho một đơn. Payload: orderId, refundedAmount (tổng đã
    /// hoàn luỹ kế, không gồm bồi thường sự cố), paidAmount (tổng đã thu) — loyalty-service thu hồi điểm đã tích.
    public static final String PAYMENT_REFUNDED = "payment.refunded";
    public static final String NOTIFICATION_REQUESTED = "notification.requested";
    public static final String LOCKER_BOX_OPENED = "locker.box.opened";
    public static final String LOCKER_BOX_FAULT = "locker.box.fault";
    public static final String LOCKER_REPORT_CLAIMED = "locker.report.claimed";
    public static final String LOCKER_REPORT_RESOLVED = "locker.report.resolved";
    /// Phiếu sự cố mới chờ KTV nhận — gửi KTV phụ trách tủ, hoặc mọi KTV tủ khi tủ chưa có người phụ trách.
    public static final String LOCKER_REPORT_ROUTED = "locker.report.routed";
    /// Admin giao việc cho một KTV cụ thể (một phiếu, hoặc quyền phụ trách một tủ).
    public static final String LOCKER_REPORT_ASSIGNED = "locker.report.assigned";
    /// Lịch kiểm tra định kỳ sắp/đã tới hạn — nhắc KTV phụ trách lịch.
    public static final String LOCKER_SCHEDULE_DUE = "locker.schedule.due";
    public static final String IOT_DEVICE_STATUS_CHANGED = "iot.device.status.changed";
    public static final String LOCKER_LAYOUT_UPDATED = "locker.layout.updated";
    /// Số ô trống của một tủ vừa đổi (ô chuyển AVAILABLE/RESERVED/OCCUPIED/FAULT hoặc đổi loại ô).
    /// Payload chỉ có lockerId — app tự tải lại ô trống; notification-service đẩy ra
    /// `/topic/public/locker-availability` cho màn đặt đơn cập nhật ngay.
    public static final String LOCKER_AVAILABILITY_CHANGED = "locker.availability.changed";
    public static final String ORDER_BOX_RELOCATED = "order.box.relocated";
    /**
     * Trạng thái chuyến giao hàng (drone) thay đổi: dispatched/approaching/arrived/delivered/delayed/failed.
     */
    public static final String DELIVERY_STATUS_CHANGED = "delivery.status.changed";
    /**
     * Trạng thái ca trực (vào ca/hết ca) hoặc toạ độ GPS của KTV (kiosk/drone) thay đổi realtime.
     */
    public static final String TECHNICIAN_PRESENCE_UPDATED = "technician.presence.updated";
    /// Số dư hoặc giao dịch ví của một người vừa đổi (nạp/trả/hoàn, rút tiền được duyệt/từ chối, admin điều
    /// chỉnh). Payload: userId, referenceType = WALLET, referenceId?, title?, message? — có `message` thì
    /// notification-service tạo thông báo, không thì chỉ đẩy `/user/queue/events` để app tải lại ví.
    public static final String WALLET_UPDATED = "wallet.updated";
    /// Điểm/tem thưởng của một người vừa đổi (cộng khi đơn hoàn tất, thu hồi khi hoàn tiền, admin điều chỉnh…).
    /// Payload như {@link #WALLET_UPDATED}, referenceType = LOYALTY.
    public static final String LOYALTY_POINTS_CHANGED = "loyalty.points.changed";
    /// Dữ liệu công khai admin vừa sửa: khuyến mãi, cửa hàng, tủ. Payload chỉ có `entity` (PROMOTION, STORE,
    /// LOCKER) và `id` — notification-service đẩy ra `/topic/public/catalog` cho app tải lại danh sách.
    public static final String CATALOG_CHANGED = "catalog.changed";
    /// Việc của KTV vừa đổi: phiếu sự cố, lịch bảo trì, tủ, drone (locker-service), đơn drone, sự cố kiện
    /// drone (order-service). Payload chỉ có `entity`, `id`, `lockerId?` — notification-service đẩy ra
    /// `/topic/staff/maintenance` (chỉ KTV + ADMIN nghe) cho app KTV tải lại ngay.
    public static final String MAINTENANCE_CHANGED = "maintenance.changed";
    /// Dữ liệu web quản trị vừa đổi do người khác: người dùng (user), đơn, phản hồi/đánh giá (order), thanh
    /// toán, hoàn tiền, rút tiền (payment). Payload chỉ có `entity`, `id` — notification-service đẩy ra
    /// `/topic/admin/changes` (chỉ ADMIN nghe) cho trang admin tải lại ngay.
    public static final String ADMIN_DATA_CHANGED = "admin.data.changed";

    private DomainEventNames() {
    }
}

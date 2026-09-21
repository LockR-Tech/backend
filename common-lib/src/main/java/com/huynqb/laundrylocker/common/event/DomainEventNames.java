package com.huynqb.laundrylocker.common.event;

public final class DomainEventNames {

    public static final String EXCHANGE = "laundry.events";
    public static final String ORDER_CREATED = "order.created";
    public static final String ORDER_STATUS_CHANGED = "order.status.changed";
    public static final String PAYMENT_COMPLETED = "payment.completed";
    public static final String PAYMENT_FAILED = "payment.failed";
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
    /**
     * Trạng thái chuyến giao hàng (drone) thay đổi: dispatched/approaching/arrived/delivered/delayed/failed.
     */
    public static final String DELIVERY_STATUS_CHANGED = "delivery.status.changed";

    private DomainEventNames() {
    }
}

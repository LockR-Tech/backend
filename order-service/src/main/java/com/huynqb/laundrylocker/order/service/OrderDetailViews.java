package com.huynqb.laundrylocker.order.service;

import com.huynqb.laundrylocker.order.dto.OrderMilestone;
import com.huynqb.laundrylocker.order.dto.OrderParcelView;
import com.huynqb.laundrylocker.order.model.DroneFailureReasons;
import com.huynqb.laundrylocker.order.model.LockerOrder;
import com.huynqb.laundrylocker.order.model.OrderStatusHistory;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/// Phần chi tiết đơn dùng chung cho người gửi (`OrderResponse`) và người nhận/người lấy hộ
/// (`ReceiverOrderDetailResponse`): khai báo kiện và các mốc thời gian, để hai màn chi tiết đọc
/// cùng một nguồn và cùng một nhãn.
public final class OrderDetailViews {

    private OrderDetailViews() {
    }

    /// Ghi chú lịch sử OrderService viết khi ô của đơn có sự cố — các mốc sự cố đọc lại từ đây vì đơn
    /// không có cột thời gian riêng cho chúng (một đơn có thể bị sự cố nhiều lần).
    static final String NOTE_BOX_INCIDENT_OPENED = "Ô của đơn đang có sự cố: tạm dừng phí quá giờ tới khi KTV xử lý";
    static final String NOTE_BOX_RELOCATED_PREFIX = "KTV điều chuyển hàng sang ô ";
    static final String NOTE_BOX_RELOCATED_SUFFIX = " do sự cố ô cũ";
    static final String NOTE_BOX_INCIDENT_CLOSED = "Đã xử lý sự cố ô";

    /// null khi đơn không khai gì về kiện — client khỏi vẽ một mục rỗng.
    public static OrderParcelView parcel(LockerOrder order) {
        boolean empty = !StringUtils.hasText(order.getDescription())
                && order.getParcelWeightGrams() == null
                && order.getActualWeight() == null
                && order.getParcelLengthCm() == null
                && order.getParcelWidthCm() == null
                && order.getParcelHeightCm() == null
                && !StringUtils.hasText(order.getParcelCategory())
                && order.getParcelDeclaredValue() == null
                && order.getParcelFragile() == null
                && order.getProhibitedItemsDeclaredAt() == null;
        if (empty) {
            return null;
        }
        return new OrderParcelView(
                StringUtils.hasText(order.getDescription()) ? order.getDescription().trim() : null,
                order.getParcelWeightGrams(),
                order.getActualWeight(),
                order.getActualWeight() == null ? null : order.getWeightUnit(),
                order.getParcelLengthCm(),
                order.getParcelWidthCm(),
                order.getParcelHeightCm(),
                order.getParcelCategory(),
                order.getParcelDeclaredValue(),
                order.getParcelFragile(),
                order.getProhibitedItemsDeclaredAt());
    }

    /// Các mốc đã xảy ra, cũ → mới. Mốc có cột thời gian riêng (tạo, thanh toán, bỏ hàng, ủy quyền,
    /// hoàn tất…) đọc từ đơn; mốc không có cột riêng (hàng vào ô, quá hạn, huỷ) lấy lần đầu đơn
    /// chuyển sang trạng thái/chặng đó trong `historyOldestFirst` (rỗng ⇒ bỏ qua các mốc này).
    /// Không đưa ghi chú hay người thao tác của lịch sử ra ngoài.
    public static List<OrderMilestone> milestones(LockerOrder order, List<OrderStatusHistory> historyOldestFirst) {
        List<OrderStatusHistory> history = historyOldestFirst == null ? List.of() : historyOldestFirst;
        boolean drone = "DRONE_DELIVERY".equalsIgnoreCase(order.getType());
        List<OrderMilestone> result = new ArrayList<>();
        add(result, "CREATED", "Đơn được tạo", order.getCreatedAt());
        add(result, "PAID", "Đã thanh toán", order.getPaidAt());
        add(result, "DROP_OPENED", "Ô được mở để bỏ hàng", order.getDropOpenedAt());
        add(result, "PARCEL_DROPPED", "Kiện đã được bỏ vào tủ gửi", order.getParcelDroppedAt());
        add(result, "STORED", drone ? "Kiện đã vào ô tủ nhận" : "Hàng đã vào ô, chờ lấy",
                firstReached(history, drone ? "READY_FOR_PICKUP" : "STORING"));
        add(result, "DELEGATED", "Đã ủy quyền lấy hộ", order.getDelegatedAt());
        addBoxIncidents(result, history);
        if (order.getDeliveryBlockReason() != null) {
            add(result, "BLOCKED", "Tạm dừng: " + DroneFailureReasons.label(order.getDeliveryBlockReason()),
                    order.getDeliveryBlockedAt());
        }
        if (order.getFailureReason() != null) {
            add(result, "FAILED", "Giao không hoàn tất: " + DroneFailureReasons.label(order.getFailureReason()),
                    order.getFailedAt());
        }
        add(result, "EXPIRED", "Quá hạn lấy hàng", firstReached(history, "EXPIRED"));
        add(result, "PARCEL_RETURNED", "Kiện đã được trả lại người gửi", order.getParcelReturnedAt());
        add(result, "CANCELED", "Đơn đã hủy", firstReached(history, "CANCELED"));
        add(result, "COMPLETED", "Đã lấy hàng, đơn hoàn tất",
                order.getCompletedAt() != null ? order.getCompletedAt() : firstReached(history, "COMPLETED"));
        // Sắp xếp ổn định: hai mốc cùng thời điểm giữ thứ tự nghiệp vụ ở trên.
        result.sort(Comparator.comparing(OrderMilestone::occurredAt));
        return List.copyOf(result);
    }

    /// Mỗi lần ô báo sự cố / KTV chuyển ô / xử lý xong là một mốc (dòng chỉ ghi chú, trạng thái giữ nguyên).
    private static void addBoxIncidents(List<OrderMilestone> result, List<OrderStatusHistory> history) {
        for (OrderStatusHistory h : history) {
            String note = h.getNote();
            if (note == null || !Objects.equals(h.getOldStatus(), h.getNewStatus())) {
                continue;
            }
            if (note.startsWith(NOTE_BOX_INCIDENT_OPENED)) {
                add(result, "BOX_INCIDENT_OPENED", "Ô báo sự cố, tạm dừng phí quá giờ", h.getCreatedAt());
            } else if (note.contains(NOTE_BOX_INCIDENT_CLOSED)) {
                add(result, "BOX_INCIDENT_RESOLVED", "Đã xử lý xong sự cố ô", h.getCreatedAt());
            } else if (note.startsWith(NOTE_BOX_RELOCATED_PREFIX) && note.endsWith(NOTE_BOX_RELOCATED_SUFFIX)) {
                String box = note.substring(
                        NOTE_BOX_RELOCATED_PREFIX.length(), note.length() - NOTE_BOX_RELOCATED_SUFFIX.length());
                add(result, "BOX_RELOCATED", "KTV chuyển đồ sang ô " + box, h.getCreatedAt());
            }
        }
    }

    private static void add(List<OrderMilestone> result, String code, String label, LocalDateTime at) {
        if (at != null) {
            result.add(new OrderMilestone(code, label, at));
        }
    }

    /// Lần đầu đơn thực sự chuyển sang `target` (bỏ các dòng chỉ ghi chú, cũ = mới).
    private static LocalDateTime firstReached(List<OrderStatusHistory> history, String target) {
        for (OrderStatusHistory h : history) {
            if (target.equalsIgnoreCase(h.getNewStatus()) && !Objects.equals(h.getOldStatus(), h.getNewStatus())) {
                return h.getCreatedAt();
            }
        }
        return null;
    }
}

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

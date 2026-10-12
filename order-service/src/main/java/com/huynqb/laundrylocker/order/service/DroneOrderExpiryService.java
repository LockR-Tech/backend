package com.huynqb.laundrylocker.order.service;

import com.huynqb.laundrylocker.common.dto.NotificationRequest;
import com.huynqb.laundrylocker.order.client.LockerClient;
import com.huynqb.laundrylocker.order.client.NotificationClient;
import com.huynqb.laundrylocker.order.model.LockerOrder;
import com.huynqb.laundrylocker.order.model.OrderStatusHistory;
import com.huynqb.laundrylocker.order.repository.LockerOrderRepository;
import com.huynqb.laundrylocker.order.repository.OrderStatusHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Đóng đơn drone chờ quá hạn ở chặng chưa có nhiệm vụ bay. Mỗi lần gọi là một giao dịch
 * riêng, khoá đơn rồi kiểm lại điều kiện: sự kiện thanh toán hoặc đội bay tiếp nhận có thể
 * vừa đổi đơn sau lúc {@link DroneOrderTimeoutSweeper} quét.
 */
@Service
@RequiredArgsConstructor
public class DroneOrderExpiryService {

    private final LockerOrderRepository orderRepository;
    private final OrderStatusHistoryRepository historyRepository;
    private final LockerClient lockerClient;
    private final NotificationClient notificationClient;

    /// Đơn chưa thu đồng nào và đội bay chưa tiếp nhận. Đơn đang nợ phụ thu cân lệch đã có
    /// nhiệm vụ và đã thu phí ban đầu — không thuộc trường hợp này.
    public static boolean unpaidPast(LockerOrder order, LocalDateTime cutoff) {
        return awaitingDispatch(order)
                && "UNPAID".equalsIgnoreCase(order.getPaymentStatus())
                && (order.getPaidAmount() == null || order.getPaidAmount().signum() <= 0)
                && order.getCreatedAt() != null
                && !order.getCreatedAt().isAfter(cutoff);
    }

    /// Đơn đã trả tiền mà chưa KTV drone nào nhận lấy hàng, tính từ lúc thanh toán.
    public static boolean unassignedPast(LockerOrder order, LocalDateTime cutoff) {
        if (!awaitingDispatch(order) || !"PAID".equalsIgnoreCase(order.getPaymentStatus())) {
            return false;
        }
        LocalDateTime since = order.getPaidAt() != null ? order.getPaidAt() : order.getCreatedAt();
        return since != null && !since.isAfter(cutoff);
    }

    /// Kiểm lại dưới khoá ngay trước khi đóng đơn: KTV có thể vừa nhận sau lúc job quét.
    @Transactional
    public boolean stillUnassigned(Long orderId, LocalDateTime cutoff) {
        LockerOrder order = orderRepository.findByIdForUpdate(orderId).orElse(null);
        return order != null && unassignedPast(order, cutoff);
    }

    @Transactional
    public boolean cancelUnpaid(Long orderId, LocalDateTime cutoff, int minutes) {
        LockerOrder order = orderRepository.findByIdForUpdate(orderId).orElse(null);
        if (order == null || !unpaidPast(order, cutoff)) {
            return false;
        }
        close(order,
                "Tự huỷ: chưa thanh toán sau " + minutes + " phút; đã nhả ô ở tủ nhận",
                "Đơn drone đã tự huỷ",
                "Đơn " + order.getOrderCode() + " chưa được thanh toán sau " + minutes
                        + " phút nên đã huỷ. Bạn có thể đặt lại đơn mới.");
        return true;
    }

    private static boolean awaitingDispatch(LockerOrder order) {
        return "DRONE_DELIVERY".equals(order.getType())
                && "AWAITING_DISPATCH".equals(order.getStatus())
                && "AWAITING_DISPATCH".equals(order.getDeliveryStage());
    }

    private void close(LockerOrder order, String historyNote, String title, String message) {
        releaseBoxQuietly(order.getReservedBoxId());
        // Kiện đã bỏ vào ô gửi thì ô đó giữ tới khi trả kiện (DroneParcelService.confirmReturn).
        if (!DroneParcelCustody.inSourceBox(order, null)) {
            releaseBoxQuietly(order.getSourceBoxId());
            order.setSourceBoxId(null);
        }
        order.setPinCode(null);
        order.setStatus("CANCELED");
        order.setDeliveryStage("CANCELED");
        orderRepository.save(order);

        OrderStatusHistory history = new OrderStatusHistory();
        history.setOrderId(order.getId());
        history.setOldStatus("AWAITING_DISPATCH");
        history.setNewStatus("CANCELED");
        history.setNote(historyNote);
        historyRepository.save(history);

        try {
            notificationClient.requestNotification(
                    new NotificationRequest(
                            order.getUserId(), title, message, "DRONE_DELIVERY_STATUS_CHANGED", order.getId(), "ORDER"));
        } catch (RuntimeException ignored) {
            // Trạng thái đơn là nguồn sự thật; thông báo chỉ là kênh báo nhanh.
        }
    }

    /// Trạng thái ô chỉ là bản sao của đơn: lỗi gọi locker-service không được giữ đơn lại,
    /// job đối soát sẽ nhả ô mồ côi sau.
    private void releaseBoxQuietly(Long boxId) {
        if (boxId == null) {
            return;
        }
        try {
            lockerClient.releaseBox(boxId);
        } catch (RuntimeException ignored) {
            // Xem chú thích trên.
        }
    }
}

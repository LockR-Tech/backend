package com.huynqb.laundrylocker.order.service;

import com.huynqb.laundrylocker.common.dto.ApiResponse;
import com.huynqb.laundrylocker.common.dto.NotificationRequest;
import com.huynqb.laundrylocker.order.client.NotificationClient;
import com.huynqb.laundrylocker.order.client.PaymentRefundClient;
import com.huynqb.laundrylocker.order.dto.OrderRefundResult;
import com.huynqb.laundrylocker.order.model.LockerOrder;
import com.huynqb.laundrylocker.order.model.OrderStatusHistory;
import com.huynqb.laundrylocker.order.repository.LockerOrderRepository;
import com.huynqb.laundrylocker.order.repository.OrderStatusHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * Hoàn tiền đơn drone bị huỷ trước khi bay.
 *
 * <p>Đơn drone phải trả tiền trước khi đội bay tiếp nhận, nên đơn bị huỷ (khách huỷ lúc
 * chờ điều phối, hoặc đội bay huỷ trước khi phóng) là đơn đã thu tiền mà không giao.
 * Chạy SAU khi việc huỷ đã commit: hoàn tiền lỗi thì đơn vẫn huỷ, ô và drone vẫn được
 * nhả, còn đơn giữ `PAID` để admin thấy và hoàn tay.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DroneRefundService {

    private final LockerOrderRepository orderRepository;
    private final OrderStatusHistoryRepository historyRepository;
    private final PaymentRefundClient paymentRefundClient;
    private final NotificationClient notificationClient;

    /// Trả về true nếu đơn đã được đánh dấu REFUNDED ở lần gọi này.
    @Transactional
    public boolean refundCanceledOrder(Long orderId, Long actorUserId) {
        LockerOrder order = orderRepository.findByIdForUpdate(orderId).orElse(null);
        if (order == null
                || !"DRONE_DELIVERY".equals(order.getType())
                || !"CANCELED".equals(order.getStatus())
                || !hasCollectedMoney(order)) {
            return false;
        }
        OrderRefundResult result;
        try {
            ApiResponse<OrderRefundResult> response =
                    paymentRefundClient.refundOrder(orderId, "Đơn drone " + order.getOrderCode() + " bị huỷ", actorUserId);
            result = response == null ? null : response.data();
        } catch (RuntimeException ex) {
            log.warn("Refund of canceled drone order {} failed, order stays PAID: {}", orderId, ex.getMessage());
            addHistory(order, actorUserId, "Hoàn tiền tự động thất bại — cần admin hoàn tay");
            return false;
        }
        if (result == null) {
            addHistory(order, actorUserId, "Hoàn tiền tự động thất bại — cần admin hoàn tay");
            return false;
        }

        order.setPaymentStatus("REFUNDED");
        orderRepository.save(order);
        BigDecimal amount = result.refundedAmount() == null ? BigDecimal.ZERO : result.refundedAmount();
        addHistory(order, actorUserId, "Đã hoàn " + amount.toBigInteger() + " đ về ví Lock.R của người đặt");
        try {
            notificationClient.requestNotification(new NotificationRequest(
                    order.getUserId(),
                    "Đã hoàn tiền đơn drone",
                    "Đơn " + order.getOrderCode() + " đã huỷ. " + amount.toBigInteger()
                            + " đ đã được hoàn về ví Lock.R của bạn.",
                    "DRONE_DELIVERY_STATUS_CHANGED",
                    order.getId(),
                    "ORDER"));
        } catch (RuntimeException ignored) {
            // Tiền đã về ví; thông báo chỉ là kênh báo nhanh.
        }
        return true;
    }

    /// Đơn đã PAID, hoặc đang nợ phụ thu cân lệch (UNPAID) nhưng đã trả phần phí ban đầu.
    private static boolean hasCollectedMoney(LockerOrder order) {
        if ("PAID".equalsIgnoreCase(order.getPaymentStatus())) {
            return true;
        }
        return "UNPAID".equalsIgnoreCase(order.getPaymentStatus())
                && order.getPaidAmount() != null
                && order.getPaidAmount().signum() > 0;
    }

    private void addHistory(LockerOrder order, Long actorUserId, String note) {
        OrderStatusHistory history = new OrderStatusHistory();
        history.setOrderId(order.getId());
        history.setOldStatus(order.getStatus());
        history.setNewStatus(order.getStatus());
        history.setChangedByUserId(actorUserId);
        history.setNote(note);
        historyRepository.save(history);
    }
}

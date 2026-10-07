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
 * Tạo yêu cầu hoàn tiền cho đơn drone bị huỷ trước khi bay.
 *
 * <p>Đơn drone phải trả tiền trước khi đội bay tiếp nhận, nên đơn bị huỷ (khách huỷ lúc
 * chờ điều phối, hoặc đội bay huỷ trước khi phóng) là đơn đã thu tiền mà không giao.
 * Chạy SAU khi việc huỷ đã commit: tạo yêu cầu lỗi thì đơn vẫn huỷ, ô và drone vẫn được
 * nhả, còn đơn giữ `PAID` để admin thấy và xử lý tay.
 *
 * <p>Tiền KHÔNG về ngay: payment-service chỉ ghi yêu cầu hoàn ở trạng thái chờ, admin chuyển
 * khoản rồi xác nhận. Đơn vì thế sang `REFUND_PENDING`; payment-service tự đổi sang
 * `REFUNDED` khi admin xác nhận đã chuyển, hoặc trả về `PAID` nếu admin từ chối.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DroneRefundService {

    private final LockerOrderRepository orderRepository;
    private final OrderStatusHistoryRepository historyRepository;
    private final PaymentRefundClient paymentRefundClient;
    private final NotificationClient notificationClient;

    /// Đơn đã có yêu cầu hoàn (đang chờ hoặc đã chuyển khoản xong).
    private static final java.util.Set<String> REFUND_STATUSES = java.util.Set.of("REFUND_PENDING", "REFUNDED");

    /// Trả về true nếu lần gọi này tạo được yêu cầu hoàn tiền (đơn sang REFUND_PENDING).
    @Transactional
    public boolean refundCanceledOrder(Long orderId, Long actorUserId) {
        return refund(orderId, actorUserId, false);
    }

    /// Yêu cầu hoàn khoản tiền tới sau khi đơn đã huỷ. Khác lần lúc huỷ: đơn có thể đã có yêu
    /// cầu hoàn cho các khoản trước, và payment-service chỉ tạo yêu cầu cho khoản chưa có.
    @Transactional
    public boolean refundLatePayment(Long orderId) {
        return refund(orderId, null, true);
    }

    private boolean refund(Long orderId, Long actorUserId, boolean latePayment) {
        LockerOrder order = orderRepository.findByIdForUpdate(orderId).orElse(null);
        if (order == null
                || !"DRONE_DELIVERY".equals(order.getType())
                || !"CANCELED".equals(order.getStatus())
                || !(latePayment || hasCollectedMoney(order))) {
            return false;
        }
        OrderRefundResult result;
        try {
            ApiResponse<OrderRefundResult> response =
                    paymentRefundClient.refundOrder(orderId, "Đơn drone " + order.getOrderCode() + " bị huỷ", actorUserId);
            result = response == null ? null : response.data();
        } catch (RuntimeException ex) {
            log.warn("Refund request for canceled drone order {} failed, order stays PAID: {}", orderId, ex.getMessage());
            addHistory(order, actorUserId, "Chưa tạo được yêu cầu hoàn tiền — cần admin xử lý tay");
            return false;
        }
        if (result == null) {
            addHistory(order, actorUserId, "Chưa tạo được yêu cầu hoàn tiền — cần admin xử lý tay");
            return false;
        }
        BigDecimal amount = result.refundedAmount() == null ? BigDecimal.ZERO : result.refundedAmount();
        if (amount.signum() <= 0) {
            // Không có khoản nào cần hoàn thêm: không được báo "hoàn 0 đ" rồi đổi trạng thái đơn.
            if (!REFUND_STATUSES.contains(order.getPaymentStatus())) {
                addHistory(order, actorUserId, "Chưa tạo được yêu cầu hoàn khoản đã thu — cần admin kiểm tra");
            }
            return false;
        }

        order.setPaymentStatus("REFUND_PENDING");
        orderRepository.save(order);
        addHistory(order, actorUserId, "Đã ghi nhận yêu cầu hoàn " + amount.toBigInteger()
                + " đ — chờ admin chuyển khoản cho người đặt");
        try {
            notificationClient.requestNotification(new NotificationRequest(
                    order.getUserId(),
                    "Đã ghi nhận yêu cầu hoàn tiền",
                    "Đơn " + order.getOrderCode() + " đã huỷ. Yêu cầu hoàn " + amount.toBigInteger()
                            + " đ đã được ghi nhận; admin sẽ chuyển khoản về tài khoản ngân hàng của bạn.",
                    "DRONE_DELIVERY_STATUS_CHANGED",
                    order.getId(),
                    "ORDER"));
        } catch (RuntimeException ignored) {
            // Yêu cầu hoàn đã được ghi; thông báo chỉ là kênh báo nhanh.
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

package com.huynqb.laundrylocker.order.service;

import com.huynqb.laundrylocker.common.exception.NotFoundException;
import com.huynqb.laundrylocker.common.security.UserRoles;
import com.huynqb.laundrylocker.order.model.LockerOrder;
import com.huynqb.laundrylocker.order.repository.LockerOrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/// Quyền đọc một đơn qua API công khai `/api/orders/{id}/**` (SEC-05). Đơn của người khác trả 404
/// giống đơn không tồn tại, để không dò được đơn nào có thật. ADMIN xem được mọi đơn.
@Component
@RequiredArgsConstructor
public class OrderAccessGuard {

    private final LockerOrderRepository orderRepository;
    private final ReceiverOrderQueryService receiverOrders;

    /// Chủ đơn hoặc ADMIN — cho các đường trả PIN/QR của đơn (chi tiết, trạng thái). Người nhận và
    /// người lấy hộ xem đơn qua `/api/orders/incoming/{id}`, nơi PIN chỉ hiện khi đến lượt họ.
    @Transactional(readOnly = true)
    public void requireOwner(Long orderId, Long userId, String roles) {
        if (UserRoles.isAdmin(roles)) {
            return;
        }
        LockerOrder order = find(orderId);
        if (userId == null || !userId.equals(order.getUserId())) {
            throw new NotFoundException("Order", orderId);
        }
    }

    /// Chủ đơn, người nhận, người lấy hộ hoặc ADMIN — cho lịch sử, đánh giá, khiếu nại (không có PIN).
    @Transactional(readOnly = true)
    public void requireParticipant(Long orderId, Long userId, String roles) {
        if (UserRoles.isAdmin(roles)) {
            return;
        }
        LockerOrder order = find(orderId);
        if (userId != null
                && (userId.equals(order.getUserId()) || receiverOrders.isReceiverOrDelegate(order, userId))) {
            return;
        }
        throw new NotFoundException("Order", orderId);
    }

    private LockerOrder find(Long orderId) {
        return orderRepository.findById(orderId).orElseThrow(() -> new NotFoundException("Order", orderId));
    }
}

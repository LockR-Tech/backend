package com.huynqb.laundrylocker.order.service;

import com.huynqb.laundrylocker.common.exception.BusinessException;
import com.huynqb.laundrylocker.order.dto.OrderBleAccessResponse;
import com.huynqb.laundrylocker.order.model.LockerOrder;
import com.huynqb.laundrylocker.order.repository.LockerOrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

/// Mở ô theo đơn qua Bluetooth (F3-G11): ai được xin mã mở ô của đơn, lúc nào và để làm gì. Cùng luật với mở
/// bằng PIN/QR (`getByAccess` + `OrderAccessPolicy`), thêm điều kiện người gọi vì mã Bluetooth gắn với tài khoản:
/// - INITIALIZED: chủ đơn mở để bỏ hàng (DROP). Đơn drone không bỏ hàng qua tủ bằng mã.
/// - Đơn thuê STORING/RETURNED: người thuê (RENTAL_ACCESS).
/// - RETURNED (trả về người gửi): chủ đơn lấy lại (PICKUP).
/// - STORING: người đang giữ mã lấy hàng — người nhận, hoặc chỉ người lấy hộ khi đơn đã ủy quyền
///   ([ReceiverOrderQueryService#holdsPickupCode]); chủ đơn tự gửi cho mình cũng được. Đơn drone chỉ khi
///   hàng đã nằm trong ô (READY_FOR_PICKUP).
@Service
@RequiredArgsConstructor
public class BleOrderAccessService {

    public static final String ACTION_DROP = "DROP";
    public static final String ACTION_RENTAL_ACCESS = "RENTAL_ACCESS";
    public static final String ACTION_PICKUP = "PICKUP";
    public static final String ORDER_NOT_FOUND = "ORDER_NOT_FOUND";
    public static final String FORBIDDEN = "BLE_ORDER_FORBIDDEN";
    public static final String NOT_OPENABLE = "BLE_ORDER_NOT_OPENABLE";

    private final LockerOrderRepository orderRepository;
    private final OrderService orderService;
    private final ReceiverOrderQueryService receiverOrders;

    @Transactional(readOnly = true)
    public OrderBleAccessResponse check(Long orderId, Long userId) {
        if (userId == null) {
            return OrderBleAccessResponse.denied(orderId, "UNAUTHORIZED", "Cần đăng nhập", 401);
        }
        LockerOrder order = orderId == null ? null : orderRepository.findById(orderId).orElse(null);
        if (order == null) {
            return OrderBleAccessResponse.denied(orderId, ORDER_NOT_FOUND, "Không tìm thấy đơn", 404);
        }
        try {
            return decide(order, userId);
        } catch (BusinessException ex) {
            // Không tra được tài khoản (user-service), chưa thanh toán…: chuyển nguyên mã cho app.
            return OrderBleAccessResponse.denied(order.getId(), ex.getCode(), ex.getMessage(), ex.getStatus().value());
        }
    }

    private OrderBleAccessResponse decide(LockerOrder order, Long userId) {
        String status = upper(order.getStatus());
        boolean rental = "RENTAL".equalsIgnoreCase(order.getType());
        boolean drone = "DRONE_DELIVERY".equalsIgnoreCase(order.getType());
        boolean owner = userId.equals(order.getUserId());
        String action;
        switch (status) {
            case "INITIALIZED" -> {
                if (!owner) {
                    return forbidden(order);
                }
                if (drone) {
                    return notOpenable(order, "Đơn giao bằng drone không bỏ hàng vào tủ bằng mã.");
                }
                action = ACTION_DROP;
            }
            case "STORING", "RETURNED" -> {
                if (rental) {
                    if (!owner) {
                        return forbidden(order);
                    }
                    action = ACTION_RENTAL_ACCESS;
                } else if ("RETURNED".equals(status)) {
                    if (!owner) {
                        return forbidden(order);
                    }
                    action = ACTION_PICKUP;
                } else {
                    if (!mayPickUp(order, userId, owner)) {
                        return forbidden(order);
                    }
                    if (drone && !"READY_FOR_PICKUP".equalsIgnoreCase(order.getDeliveryStage())) {
                        return notOpenable(order, "Hàng chưa được đưa vào ô, chưa mở ô được.");
                    }
                    action = ACTION_PICKUP;
                }
            }
            default -> {
                if (owner || receiverOrders.isReceiverOrDelegate(order, userId)) {
                    return notOpenable(order, "Đơn này không còn mở ô được.");
                }
                return forbidden(order);
            }
        }
        Long boxId = order.getReceiveBoxId() != null ? order.getReceiveBoxId() : order.getSendBoxId();
        if (boxId == null || order.getLockerId() == null) {
            return notOpenable(order, "Đơn này không gắn với ô tủ nào.");
        }
        orderService.assertPaidBeforeAccess(order);
        String blockReason = orderService.accessBlockReason(order);
        if (blockReason != null) {
            return OrderBleAccessResponse.denied(order.getId(), blockReason, null, 409);
        }
        return new OrderBleAccessResponse(order.getId(), order.getUserId(), order.getLockerId(), boxId,
                order.getStatus(), order.getType(), action, null, null, null);
    }

    /// Người nhận / người lấy hộ đang giữ mã; chủ đơn chỉ khi tự gửi cho mình và chưa ủy quyền cho ai.
    private boolean mayPickUp(LockerOrder order, Long userId, boolean owner) {
        if (owner && order.getDelegatePhone() == null
                && (userId.equals(order.getReceiverUserId()) || userId.equals(order.getReceiverId()))) {
            return true;
        }
        return receiverOrders.holdsPickupCode(order, userId);
    }

    private static OrderBleAccessResponse forbidden(LockerOrder order) {
        return OrderBleAccessResponse.denied(order.getId(), FORBIDDEN,
                "Tài khoản này không được mở ô của đơn lúc này.", 403);
    }

    private static OrderBleAccessResponse notOpenable(LockerOrder order, String message) {
        return OrderBleAccessResponse.denied(order.getId(), NOT_OPENABLE, message, 409);
    }

    private static String upper(String value) {
        return value == null ? "" : value.toUpperCase(Locale.ROOT);
    }
}

package com.huynqb.laundrylocker.order.service;

import com.huynqb.laundrylocker.order.model.LockerOrder;

/// Bước tiếp theo của đơn (nextAction/nextActionMessage/paymentRequired) — tách khỏi
/// OrderService để OrderResponse của mobile và AdminOrderResponse dùng chung một quy tắc.
public final class OrderNextActions {

    private OrderNextActions() {
    }

    public static String nextAction(LockerOrder order) {
        if ("DRONE_DELIVERY".equalsIgnoreCase(order.getType())) {
            String deliveryStage = order.getDeliveryStage() == null ? "" : order.getDeliveryStage().toUpperCase();
            if ("READY_FOR_PICKUP".equals(deliveryStage)) {
                return "PAID".equalsIgnoreCase(order.getPaymentStatus()) ? "PICKUP" : "PAY_BEFORE_PICKUP";
            }
            // Đã báo rơi kiện: đơn vẫn AWAITING_DISPATCH nhưng chuyến bay đã dừng — không được hiện như
            // đang chờ đội bay; khách theo dõi ở mục sự cố (/api/orders/drone-incidents).
            if ("DROP_REPORTED".equals(deliveryStage) && "AWAITING_DISPATCH".equals(order.getStatus())) {
                return "INCIDENT_IN_PROGRESS";
            }
            return switch (order.getStatus()) {
                // Đội bay chỉ tiếp nhận / chỉ phóng khi đơn đã trả đủ (kể cả phụ thu cân lệch).
                case "AWAITING_DISPATCH" -> {
                    if (!"PAID".equalsIgnoreCase(order.getPaymentStatus())) {
                        yield "PAY_FOR_DRONE";
                    }
                    if (order.getPickupLatitude() != null) {
                        // Lấy hàng tận nhà: chờ KTV drone tới lấy, sau đó chờ drone bay.
                        yield "AWAITING_DISPATCH".equals(deliveryStage) || "PICKUP_ASSIGNED".equals(deliveryStage)
                                ? "WAIT_FOR_PICKUP"
                                : "WAIT_FOR_DRONE";
                    }
                    // Đơn cũ: đội bay chỉ tiếp nhận khi người gửi đã xác nhận bỏ kiện vào ô gửi.
                    yield "AWAITING_DISPATCH".equals(deliveryStage) && order.getParcelDroppedAt() == null
                            ? "DROP_PARCEL_FOR_DRONE"
                            : "WAIT_FOR_DRONE";
                }
                case "COMPLETED" -> "DONE";
                case "CANCELED" -> "CANCELED";
                case "EXPIRED" -> "CONTACT_STAFF";
                default -> "UNKNOWN";
            };
        }
        if (OrderAccessPolicy.boxIncidentBlocksAccess(order)) {
            return "WAIT_FOR_BOX_INCIDENT";
        }
        return switch (order.getStatus()) {
            case "INITIALIZED" -> "PAY_AND_DROP";
            case "STORING" -> "PICKUP";
            case "COMPLETED" -> "DONE";
            case "CANCELED" -> "CANCELED";
            case "EXPIRED" -> "CONTACT_STAFF";
            default -> "UNKNOWN";
        };
    }

    public static String nextActionMessage(LockerOrder order) {
        return switch (nextAction(order)) {
            case "PAY_AND_DROP" -> "Pay and place storage items in locker.";
            case "WAIT_FOR_DRONE" -> "Maintenance will accept and launch the drone delivery.";
            case "WAIT_FOR_PICKUP" -> "A drone technician will come to the pickup address to collect the parcel.";
            case "DROP_PARCEL_FOR_DRONE" ->
                    "Place the parcel in the drone cell at the source locker and confirm the drop-off.";
            case "PAY_FOR_DRONE" -> "Pay for the drone delivery so the flight team can dispatch it.";
            case "PAY_BEFORE_PICKUP" -> "Pay for the drone delivery before opening the locker.";
            case "INCIDENT_IN_PROGRESS" -> "A delivery incident is being handled; follow the incident for updates.";
            case "WAIT_FOR_BOX_INCIDENT" ->
                    "The box is being handled by a technician; overtime fees are paused until it is fixed.";
            case "PICKUP" -> "Pick up items from locker.";
            case "CONTACT_STAFF" -> "Items moved to storage; contact staff to retrieve them.";
            default -> order.getStatus();
        };
    }

    public static boolean paymentRequired(LockerOrder order) {
        if ("DRONE_DELIVERY".equalsIgnoreCase(order.getType())) {
            boolean active = "AWAITING_DISPATCH".equals(order.getStatus())
                    || "READY_FOR_PICKUP".equalsIgnoreCase(order.getDeliveryStage());
            return active && "UNPAID".equalsIgnoreCase(order.getPaymentStatus());
        }
        return "INITIALIZED".equals(order.getStatus());
    }
}

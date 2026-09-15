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
            return switch (order.getStatus()) {
                case "AWAITING_DISPATCH" -> "WAIT_FOR_DRONE";
                case "COMPLETED" -> "DONE";
                case "CANCELED" -> "CANCELED";
                default -> "UNKNOWN";
            };
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
            case "PAY_BEFORE_PICKUP" -> "Pay for the drone delivery before opening the locker.";
            case "PICKUP" -> "Pick up items from locker.";
            case "CONTACT_STAFF" -> "Items moved to storage; contact staff to retrieve them.";
            default -> order.getStatus();
        };
    }

    public static boolean paymentRequired(LockerOrder order) {
        if ("DRONE_DELIVERY".equalsIgnoreCase(order.getType())) {
            return "READY_FOR_PICKUP".equalsIgnoreCase(order.getDeliveryStage())
                    && !"PAID".equalsIgnoreCase(order.getPaymentStatus());
        }
        return "INITIALIZED".equals(order.getStatus());
    }
}

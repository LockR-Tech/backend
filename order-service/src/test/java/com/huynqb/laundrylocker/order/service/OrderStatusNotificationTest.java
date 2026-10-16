package com.huynqb.laundrylocker.order.service;

import com.huynqb.laundrylocker.order.model.LockerOrder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OrderStatusNotificationTest {

    @Test
    @DisplayName("hoàn tất / huỷ báo bằng tiếng Việt, kèm mã đơn")
    void completedAndCanceledAreVietnamese() {
        assertThat(OrderService.statusNotification(order("SEND", "COMPLETED", "PAID")))
                .containsExactly("Đơn hàng hoàn tất", "Đơn ORD-1 đã hoàn tất. Cảm ơn bạn đã dùng Lock.R!");
        assertThat(OrderService.statusNotification(order("RENTAL", "CANCELED", "UNPAID")))
                .containsExactly("Đơn hàng đã huỷ", "Đơn ORD-1 đã được huỷ.");
        assertThat(OrderService.statusNotification(order("SEND", "CANCELED", "PAID"))[1])
                .contains("sẽ được hoàn");
    }

    @Test
    @DisplayName("bước đã có thông báo riêng thì không báo thêm (gửi hàng vào ô, chuyển kho)")
    void stepsWithDedicatedNotificationAreSkipped() {
        assertThat(OrderService.statusNotification(order("SEND", "STORING", "PAID"))).isNull();
        assertThat(OrderService.statusNotification(order("RENTAL", "EXPIRED", "PAID"))).isNull();
        assertThat(OrderService.statusNotification(order("RENTAL", "STORING", "PAID"))[0]).isEqualTo("Bắt đầu thuê ô");
    }

    private static LockerOrder order(String type, String status, String paymentStatus) {
        LockerOrder order = new LockerOrder();
        order.setOrderCode("ORD-1");
        order.setType(type);
        order.setStatus(status);
        order.setPaymentStatus(paymentStatus);
        return order;
    }
}

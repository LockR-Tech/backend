package com.huynqb.laundrylocker.order.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DroneDeliveryEmailTemplateTest {

    @Test
    void launchTemplateHasBrandAndOrderDetailsAndEscapesUserValues() {
        String html = DroneDeliveryEmailTemplate.launched(
                "<script>alert(1)</script>",
                "ORD-42&43",
                "Đơn hàng đang trên đường",
                "Đơn hàng đã được phóng đi.",
                List.of(new DroneDeliveryEmailTemplate.Detail("Người nhận", "An <an@example.com>")),
                "Mở ứng dụng Lock.R để theo dõi.");

        assertThat(html)
                .contains("Lock.R", "SMART LOCKER", "cid:lockr-logo", "ORD-42&amp;43", "An &lt;an@example.com&gt;")
                .doesNotContain("<script>");
    }

    @Test
    void failedDeliveryTemplateIncludesReasonAndRefundSection() {
        String html = DroneDeliveryEmailTemplate.failed(
                "Minh Anh",
                "ORD-87",
                "Đơn chưa thể giao hoàn tất.",
                List.of(
                        new DroneDeliveryEmailTemplate.Detail("Lý do", "Drone gặp sự cố"),
                        new DroneDeliveryEmailTemplate.Detail("Hoàn tiền", "Đã tạo yêu cầu hoàn tiền")),
                "Xem trạng thái trong ứng dụng Lock.R.");

        assertThat(html).contains("CẬP NHẬT QUAN TRỌNG", "Drone gặp sự cố", "Đã tạo yêu cầu hoàn tiền");
    }
}

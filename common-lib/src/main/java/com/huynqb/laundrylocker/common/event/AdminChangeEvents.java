package com.huynqb.laundrylocker.common.event;

import org.springframework.amqp.rabbit.core.RabbitTemplate;

/// Phát {@link DomainEventNames#ADMIN_DATA_CHANGED} khi dữ liệu web quản trị đang xem đổi do người khác
/// (khách đăng ký/sửa hồ sơ, tạo thanh toán/yêu cầu hoàn tiền/rút tiền, gửi phản hồi/đánh giá…) để trang
/// admin tải lại ngay thay vì phải bấm làm mới. Gom theo giao dịch, gửi sau commit: {@link EntityChangeEvents}.
public final class AdminChangeEvents {

    public static final String USER = "USER";
    public static final String ORDER = "ORDER";
    public static final String PAYMENT = "PAYMENT";
    public static final String REFUND = "REFUND";
    public static final String WITHDRAWAL = "WITHDRAWAL";
    public static final String FEEDBACK = "FEEDBACK";

    private AdminChangeEvents() {
    }

    public static void changed(RabbitTemplate rabbitTemplate, String source, String entity, Long id) {
        EntityChangeEvents.changed(rabbitTemplate, DomainEventNames.ADMIN_DATA_CHANGED, source, entity, id, null);
    }
}

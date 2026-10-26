package com.huynqb.laundrylocker.payment.model;

import com.huynqb.laundrylocker.common.event.AdminChangeEvents;
import jakarta.persistence.PostPersist;
import jakarta.persistence.PostRemove;
import jakarta.persistence.PostUpdate;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/// Báo web quản trị khi thanh toán, yêu cầu hoàn tiền hay yêu cầu rút tiền đổi — khách tạo, webhook SePay
/// ghi nhận, admin khác duyệt — để trang Thanh toán/Hoàn tiền/Ví tải lại ngay. Số dư ví đã có `wallet.updated`.
@Component
public class AdminDataChangeListener {

    private final ObjectProvider<RabbitTemplate> rabbitTemplate;

    public AdminDataChangeListener(ObjectProvider<RabbitTemplate> rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    @PostPersist
    @PostUpdate
    @PostRemove
    void changed(Object entity) {
        switch (entity) {
            case PaymentRecord payment -> publish(AdminChangeEvents.PAYMENT, payment.getId());
            case RefundRecord refund -> publish(AdminChangeEvents.REFUND, refund.getId());
            case WithdrawalRecord withdrawal -> publish(AdminChangeEvents.WITHDRAWAL, withdrawal.getId());
            default -> {
            }
        }
    }

    private void publish(String entity, Long id) {
        AdminChangeEvents.changed(rabbitTemplate.getIfAvailable(), "payment-service", entity, id);
    }
}

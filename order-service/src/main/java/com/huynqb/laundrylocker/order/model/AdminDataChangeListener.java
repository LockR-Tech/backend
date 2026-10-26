package com.huynqb.laundrylocker.order.model;

import com.huynqb.laundrylocker.common.event.AdminChangeEvents;
import jakarta.persistence.PostPersist;
import jakarta.persistence.PostRemove;
import jakarta.persistence.PostUpdate;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/// Báo web quản trị khi đơn (mọi loại, mọi luồng ghi — kể cả quét định kỳ, kiosk) hay phản hồi/đánh giá/khiếu
/// nại của khách đổi, để trang Đơn hàng, Phản hồi, Tổng quan tải lại ngay.
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
            case LockerOrder order -> publish(AdminChangeEvents.ORDER, order.getId());
            case OrderRating rating -> publish(AdminChangeEvents.FEEDBACK, rating.getId());
            case OrderComplaint complaint -> publish(AdminChangeEvents.FEEDBACK, complaint.getId());
            default -> {
            }
        }
    }

    private void publish(String entity, Long id) {
        AdminChangeEvents.changed(rabbitTemplate.getIfAvailable(), "order-service", entity, id);
    }
}

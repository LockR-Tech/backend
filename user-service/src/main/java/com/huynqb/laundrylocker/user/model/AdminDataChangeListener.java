package com.huynqb.laundrylocker.user.model;

import com.huynqb.laundrylocker.common.event.AdminChangeEvents;
import jakarta.persistence.PostPersist;
import jakarta.persistence.PostRemove;
import jakarta.persistence.PostUpdate;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/// Báo web quản trị khi hồ sơ người dùng đổi — khách đăng ký, sửa hồ sơ, admin khác khoá/đổi vai trò — để
/// trang Người dùng và chi tiết người dùng tải lại ngay.
@Component
public class AdminDataChangeListener {

    private final ObjectProvider<RabbitTemplate> rabbitTemplate;

    public AdminDataChangeListener(ObjectProvider<RabbitTemplate> rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    @PostPersist
    @PostUpdate
    @PostRemove
    void changed(UserProfile profile) {
        AdminChangeEvents.changed(rabbitTemplate.getIfAvailable(), "user-service", AdminChangeEvents.USER, profile.getId());
    }
}

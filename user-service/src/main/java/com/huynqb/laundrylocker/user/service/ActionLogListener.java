package com.huynqb.laundrylocker.user.service;

import com.huynqb.laundrylocker.common.event.DomainEvent;
import com.huynqb.laundrylocker.user.config.ActionLogRabbitConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/// Nhận `admin.action.logged` từ mọi service và lưu (F3-G16). Lỗi một dòng chỉ ghi cảnh báo — không chặn hàng đợi.
@Slf4j
@Component
@RequiredArgsConstructor
public class ActionLogListener {

    private final ActionLogService service;

    @RabbitListener(queues = ActionLogRabbitConfig.ACTION_LOG_QUEUE)
    public void onEvent(DomainEvent event) {
        try {
            service.store(event);
        } catch (RuntimeException ex) {
            log.warn("Could not store action log {}: {}", event == null ? null : event.eventId(), ex.getMessage());
        }
    }
}

package com.huynqb.laundrylocker.locker.model;

import com.huynqb.laundrylocker.common.event.DomainEvent;
import com.huynqb.laundrylocker.common.event.DomainEventNames;
import jakarta.persistence.PostPersist;
import jakarta.persistence.PostRemove;
import jakarta.persistence.PostUpdate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/// Báo "số ô trống của tủ X vừa đổi" mỗi khi một ô đổi trạng thái hoặc loại ô — bất kể đi qua luồng
/// nào (đặt đơn, lấy hàng xong, huỷ, hết hạn, báo hỏng…), để màn đặt đơn drone cập nhật ngay ô trống.
///
/// Chỉ gửi SAU KHI giao dịch commit (client tải lại phải thấy dữ liệu mới), và mỗi tủ một lần cho
/// một giao dịch dù giao dịch đổi nhiều ô.
@Slf4j
@Component
public class LockerBoxAvailabilityListener {

    private static final String PENDING_KEY = LockerBoxAvailabilityListener.class.getName() + ".pending";

    private final ObjectProvider<RabbitTemplate> rabbitTemplate;

    public LockerBoxAvailabilityListener(ObjectProvider<RabbitTemplate> rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    @PostPersist
    @PostRemove
    void boxAddedOrRemoved(LockerBox box) {
        changed(box.getLockerId());
    }

    @PostUpdate
    void boxUpdated(LockerBox box) {
        String now = box.availabilityKey();
        if (now.equals(box.getLoadedAvailabilityKey())) {
            return;
        }
        box.setLoadedAvailabilityKey(now);
        changed(box.getLockerId());
    }

    @SuppressWarnings("unchecked")
    void changed(Long lockerId) {
        if (lockerId == null) {
            return;
        }
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            publish(lockerId);
            return;
        }
        Set<Long> pending = (Set<Long>) TransactionSynchronizationManager.getResource(PENDING_KEY);
        if (pending == null) {
            Set<Long> lockers = new LinkedHashSet<>();
            TransactionSynchronizationManager.bindResource(PENDING_KEY, lockers);
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    lockers.forEach(LockerBoxAvailabilityListener.this::publish);
                }

                @Override
                public void afterCompletion(int status) {
                    TransactionSynchronizationManager.unbindResourceIfPossible(PENDING_KEY);
                }
            });
            pending = lockers;
        }
        pending.add(lockerId);
    }

    private void publish(Long lockerId) {
        RabbitTemplate rabbit = rabbitTemplate.getIfAvailable();
        if (rabbit == null) {
            return;
        }
        try {
            rabbit.convertAndSend(
                    DomainEventNames.EXCHANGE,
                    DomainEventNames.LOCKER_AVAILABILITY_CHANGED,
                    DomainEvent.of(
                            DomainEventNames.LOCKER_AVAILABILITY_CHANGED,
                            "locker-service",
                            Map.of("lockerId", lockerId)));
        } catch (AmqpException ex) {
            log.warn("Could not publish locker.availability.changed for locker {}: {}", lockerId, ex.getMessage());
        }
    }
}

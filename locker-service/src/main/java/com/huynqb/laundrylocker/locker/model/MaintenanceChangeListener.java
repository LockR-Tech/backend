package com.huynqb.laundrylocker.locker.model;

import com.huynqb.laundrylocker.common.event.MaintenanceChangeEvents;
import jakarta.persistence.PostPersist;
import jakarta.persistence.PostRemove;
import jakarta.persistence.PostUpdate;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/// Báo app KTV mỗi khi việc bảo trì đổi — phiếu sự cố (kể cả nhật ký sửa, ảnh, đánh giá của khách), lịch
/// kiểm tra định kỳ, tủ (phân công phụ trách, bảo trì) và drone — bất kể ai đổi và đổi qua luồng nào.
/// Ô tủ đổi trạng thái đã có `locker.availability.changed` ({@link LockerBoxAvailabilityListener}).
@Component
public class MaintenanceChangeListener {

    private final ObjectProvider<RabbitTemplate> rabbitTemplate;

    public MaintenanceChangeListener(ObjectProvider<RabbitTemplate> rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    @PostPersist
    @PostUpdate
    @PostRemove
    void changed(Object entity) {
        switch (entity) {
            case LockerReport report -> publish(MaintenanceChangeEvents.REPORT, report.getId(), report.getLockerId());
            case RepairLog log -> publish(MaintenanceChangeEvents.REPORT, log.getReportId(), null);
            case ReportAttachment attachment ->
                    publish(MaintenanceChangeEvents.REPORT, attachment.getReportId(), null);
            case LockerReportRating rating -> publish(MaintenanceChangeEvents.REPORT, rating.getReportId(), null);
            case MaintenanceSchedule schedule ->
                    publish(MaintenanceChangeEvents.SCHEDULE, schedule.getId(), schedule.getLockerId());
            case MaintenanceInspectionLog inspection ->
                    publish(MaintenanceChangeEvents.SCHEDULE, inspection.getScheduleId(), inspection.getLockerId());
            case LockerUnit locker -> publish(MaintenanceChangeEvents.LOCKER, locker.getId(), locker.getId());
            case DroneUnit drone -> publish(MaintenanceChangeEvents.DRONE, drone.getId(), drone.getLockerId());
            case DroneMaintenanceLog log -> publish(MaintenanceChangeEvents.DRONE, log.getDroneUnitId(), null);
            default -> {
            }
        }
    }

    private void publish(String entity, Long id, Long lockerId) {
        MaintenanceChangeEvents.changed(rabbitTemplate.getIfAvailable(), "locker-service", entity, id, lockerId);
    }
}
